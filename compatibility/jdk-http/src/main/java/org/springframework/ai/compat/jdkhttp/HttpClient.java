/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.compat.jdkhttp;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Asynchronous Java 8 HTTP bridge for MCP's JDK HTTP transport surface. Uses Apache
 * HttpClient 5 and Reactor; response bodies remain demand-driven and cancellable.
 *
 * @author 芋道源码
 */
public final class HttpClient implements AutoCloseable {

	private static final AtomicInteger THREAD_IDS = new AtomicInteger();

	private static final String EXCHANGE_ATTRIBUTE = HttpClient.class.getName() + ".exchange";

	private final CloseableHttpAsyncClient client;

	private final CookieHandler cookies;

	private final Executor executor;

	private final Redirect redirects;

	private final Version version;

	private final Duration connectTimeout;

	private final Builder configuration;

	private HttpClient alternate;

	private boolean closed;

	private HttpClient(Builder builder) {
		this.cookies = builder.cookies;
		this.executor = builder.executor;
		this.redirects = builder.redirects;
		this.version = builder.version;
		this.connectTimeout = builder.connectTimeout;
		SSLContext ssl;
		try {
			ssl = builder.sslContext == null ? SSLContext.getDefault() : builder.sslContext;
		}
		catch (java.security.NoSuchAlgorithmException error) {
			throw new IllegalStateException(error);
		}
		this.configuration = new Builder(builder);
		this.configuration.sslContext = ssl;
		final SSLParameters supplied = this.configuration.sslParameters;
		DefaultClientTlsStrategy tls = new DefaultClientTlsStrategy(ssl) {

			@Override
			protected void initializeEngine(SSLEngine engine) {
				super.initializeEngine(engine);
				if (supplied == null) {
					return;
				}
				SSLParameters merged = engine.getSSLParameters();
				if (supplied.getProtocols() != null) {
					merged.setProtocols(supplied.getProtocols());
				}
				if (supplied.getCipherSuites() != null) {
					merged.setCipherSuites(supplied.getCipherSuites());
				}
				if (supplied.getAlgorithmConstraints() != null) {
					merged.setAlgorithmConstraints(supplied.getAlgorithmConstraints());
				}
				if (supplied.getEndpointIdentificationAlgorithm() != null) {
					merged.setEndpointIdentificationAlgorithm(supplied.getEndpointIdentificationAlgorithm());
				}
				if (supplied.getServerNames() != null) {
					merged.setServerNames(supplied.getServerNames());
				}
				if (supplied.getSNIMatchers() != null) {
					merged.setSNIMatchers(supplied.getSNIMatchers());
				}
				merged.setUseCipherSuitesOrder(supplied.getUseCipherSuitesOrder());
				engine.setSSLParameters(merged);
			}
		};
		ConnectionConfig.Builder connection = ConnectionConfig.custom().setSocketTimeout(Timeout.DISABLED);
		if (builder.connectTimeout != null) {
			connection.setConnectTimeout(Timeout.ofMilliseconds(builder.connectTimeout.toMillis()));
		}
		org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder http = HttpAsyncClients.custom()
			.setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
				.setTlsStrategy(tls)
				.setDefaultConnectionConfig(connection.build())
				.build())
			.setIOReactorConfig(IOReactorConfig.custom().setIoThreadCount(2).build())
			.setThreadFactory(task -> {
				Thread thread = new Thread(task, "mcp-http-" + THREAD_IDS.incrementAndGet());
				thread.setDaemon(true);
				return thread;
			})
			.setVersionPolicy(
					builder.version == Version.HTTP_1_1 ? HttpVersionPolicy.FORCE_HTTP_1 : HttpVersionPolicy.NEGOTIATE)
			.disableAutomaticRetries()
			.disableCookieManagement();
		http.addExecInterceptorLast("java8-cancellation", (outgoing, entity, scope, chain, callback) -> {
			Object state = scope.clientContext.getAttribute(EXCHANGE_ATTRIBUTE);
			if (state instanceof Exchange && !((Exchange) state).attach(scope.execRuntime)) {
				callback.failed(new java.util.concurrent.CancellationException("HTTP exchange cancelled"));
				return;
			}
			chain.proceed(outgoing, entity, scope, callback);
		});
		ProxySelector proxy = this.configuration.proxy;
		if (proxy != null) {
			http.setRoutePlanner(new SystemDefaultRoutePlanner(proxy));
		}
		if (builder.proxyUsername != null) {
			final org.apache.hc.client5.http.auth.UsernamePasswordCredentials credentials = new org.apache.hc.client5.http.auth.UsernamePasswordCredentials(
					builder.proxyUsername, builder.proxyPassword.clone());
			http.setDefaultCredentialsProvider((scope, context) -> {
				org.apache.hc.client5.http.RouteInfo route = HttpClientContext.castOrCreate(context).getHttpRoute();
				org.apache.hc.core5.http.HttpHost proxyHost = route == null ? null : route.getProxyHost();
				if (proxyHost != null && proxyHost.getHostName().equalsIgnoreCase(scope.getHost())
						&& proxyHost.getPort() == scope.getPort()) {
					return credentials;
				}
				return null; // Never offer proxy credentials for a direct/origin-server
								// challenge.
			});
		}
		final Redirect policy = this.redirects;
		http.setRedirectStrategy(new org.apache.hc.client5.http.impl.DefaultRedirectStrategy() {

			@Override
			public boolean isRedirectAllowed(org.apache.hc.core5.http.HttpHost current,
					org.apache.hc.core5.http.HttpHost next, org.apache.hc.core5.http.HttpRequest request,
					org.apache.hc.core5.http.protocol.HttpContext context) {
				if (policy == Redirect.NORMAL && "https".equalsIgnoreCase(current.getSchemeName())
						&& "http".equalsIgnoreCase(next.getSchemeName())) {
					return false;
				}
				return super.isRedirectAllowed(current, next, request, context);
			}
		});
		if (this.cookies != null) {
			http.addRequestInterceptorLast((outgoing, entity, context) -> {
				URI uri;
				try {
					uri = outgoing.getUri();
				}
				catch (java.net.URISyntaxException error) {
					throw new org.apache.hc.core5.http.ProtocolException("Invalid request URI", error);
				}
				this.cookies.get(uri, headerMap(outgoing))
					.forEach((name, values) -> values.forEach(value -> outgoing.addHeader(name, value)));
			});
			http.addResponseInterceptorLast((response, entity, context) -> {
				try {
					URI uri = HttpClientContext.castOrCreate(context).getRequest().getUri();
					this.cookies.put(uri, headerMap(response));
				}
				catch (java.net.URISyntaxException error) {
					throw new org.apache.hc.core5.http.ProtocolException("Invalid request URI", error);
				}
			});
		}
		this.client = http.build();
		this.client.start();
	}

	public java.util.Optional<Duration> connectTimeout() {
		return java.util.Optional.ofNullable(this.connectTimeout);
	}

	/**
	 * Return this client's configured HTTP protocol preference.
	 * @since 2.0.0
	 */
	public Version version() {
		return this.version;
	}

	/**
	 * Return this client's configured redirect policy.
	 * @since 2.0.0
	 */
	public Redirect followRedirects() {
		return this.redirects;
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static HttpClient newHttpClient() {
		return newBuilder().build();
	}

	public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
			throws IOException, InterruptedException {
		CompletableFuture<HttpResponse<T>> result = sendAsync(request, handler);
		try {
			return result.get();
		}
		catch (InterruptedException interrupted) {
			result.cancel(true);
			throw interrupted;
		}
		catch (java.util.concurrent.ExecutionException error) {
			Throwable cause = error.getCause();
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IOException("HTTP exchange failed", cause);
		}
	}

	public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
		Objects.requireNonNull(request);
		Objects.requireNonNull(handler);
		HttpClient selected;
		synchronized (this) {
			if (this.closed) {
				CompletableFuture<HttpResponse<T>> rejected = new CompletableFuture<>();
				rejected.completeExceptionally(new IOException("HTTP client is closed"));
				return rejected;
			}
			selected = this;
			if (request.version().isPresent() && request.version().get() != this.version) {
				// Protocol policy belongs to the connection pool. Keep one lazy pool for
				// the other protocol instead of reusing a connection with the wrong
				// policy.
				if (this.alternate == null) {
					this.alternate = new HttpClient(new Builder(this.configuration).version(request.version().get()));
				}
				selected = this.alternate;
			}
		}
		if (selected != this) {
			return selected.sendAsync(request, handler);
		}
		HttpClientContext context = HttpClientContext.create();
		context.setRequestConfig(RequestConfig.custom()
			.setRedirectsEnabled(this.redirects != Redirect.NEVER)
			.setExpectContinueEnabled(request.expectContinue())
			.setHardCancellationEnabled(true)
			.build());
		org.apache.hc.core5.http.message.BasicHttpRequest outgoing = new org.apache.hc.core5.http.message.BasicHttpRequest(
				request.method(), request.uri());
		request.headers().map().forEach((name, values) -> values.forEach(value -> outgoing.addHeader(name, value)));
		org.apache.hc.core5.http.nio.AsyncEntityProducer entity = null;
		if (request.bodyPublisher().isPresent()) {
			HttpRequest.BodyPublisher body = request.bodyPublisher().get();
			String contentType = request.headers().firstValue("Content-Type").orElse(null);
			String encoding = request.headers().firstValue("Content-Encoding").orElse(null);
			if (contentType == null) {
				outgoing.setHeader("Content-Type", "application/octet-stream");
			}
			entity = new RequestEntityProducer(body, contentType == null ? "application/octet-stream" : contentType,
					encoding);
		}
		org.apache.hc.core5.http.nio.support.BasicRequestProducer producer = new org.apache.hc.core5.http.nio.support.BasicRequestProducer(
				outgoing, entity);
		Exchange exchange = new Exchange();
		context.setAttribute(EXCHANGE_ATTRIBUTE, exchange);
		Mono<org.apache.hc.core5.http.Message<org.apache.hc.core5.http.HttpResponse, org.reactivestreams.Publisher<ByteBuffer>>> headers = Mono
			.create(sink -> {
				sink.onCancel(exchange::cancel);
				org.apache.hc.core5.reactive.ReactiveResponseConsumer consumer = new org.apache.hc.core5.reactive.ReactiveResponseConsumer(
						new org.apache.hc.core5.concurrent.FutureCallback<org.apache.hc.core5.http.Message<org.apache.hc.core5.http.HttpResponse, org.reactivestreams.Publisher<ByteBuffer>>>() {
							public void completed(
									org.apache.hc.core5.http.Message<org.apache.hc.core5.http.HttpResponse, org.reactivestreams.Publisher<ByteBuffer>> response) {
								sink.success(response);
							}

							public void failed(Exception error) {
								sink.error(error);
							}

							public void cancelled() {
								sink.error(new IOException("HTTP exchange cancelled"));
							}
						});
				exchange.attach(this.client.execute(producer, consumer, context, null));
			});
		Mono<HttpResponse<T>> result = headers.flatMap(response -> {
			try {
				java.util.Map<String, List<String>> values = new java.util.LinkedHashMap<>();
				for (org.apache.hc.core5.http.Header header : response.getHead().getHeaders()) {
					values
						.computeIfAbsent(header.getName().toLowerCase(java.util.Locale.ROOT),
								name -> new java.util.ArrayList<>())
						.add(header.getValue());
				}
				HttpHeaders responseHeaders = HttpHeaders.of(values, (name, entry) -> true);
				Flux<List<ByteBuffer>> chunks = Flux.from(response.getBody())
					.map(buffer -> Collections.singletonList(buffer.asReadOnlyBuffer()))
					.doOnCancel(exchange::cancel);
				Flow.Publisher<List<ByteBuffer>> body = FlowAdapters.publisherToFlowPublisher(chunks);
				Version protocol = context.getProtocolVersion() != null && context.getProtocolVersion().getMajor() >= 2
						? Version.HTTP_2 : Version.HTTP_1_1;
				URI responseUri = context.getRequest().getUri();
				return Mono.fromCompletionStage(handler.apply(body))
					.<HttpResponse<T>>map(value -> new Response<>(request, response.getHead().getCode(),
							responseHeaders, value, protocol, responseUri))
					.defaultIfEmpty(new Response<>(request, response.getHead().getCode(), responseHeaders, null,
							protocol, responseUri));
			}
			catch (Throwable error) {
				exchange.cancel();
				return Mono.error(error);
			}
		})
			.doOnCancel(exchange::cancel)
			.doOnError(error -> exchange.cancel())
			.doOnDiscard(HttpResponse.class, HttpClient::discard);
		if (this.executor != null) {
			result = result.publishOn(Schedulers.fromExecutor(this.executor));
		}
		if (request.timeout().isPresent()) {
			result = result.timeout(request.timeout().get());
		}
		return result.onErrorMap(HttpClient::timeoutError).toFuture();
	}

	private static java.util.Map<String, List<String>> headerMap(org.apache.hc.core5.http.HttpMessage message) {
		java.util.Map<String, List<String>> headers = new java.util.LinkedHashMap<>();
		for (org.apache.hc.core5.http.Header header : message.getHeaders()) {
			headers
				.computeIfAbsent(header.getName().toLowerCase(java.util.Locale.ROOT),
						key -> new java.util.ArrayList<>())
				.add(header.getValue());
		}
		return headers;
	}

	private static void discard(HttpResponse<?> response) {
		if (response.body() instanceof java.io.InputStream) {
			try {
				((java.io.InputStream) response.body()).close();
			}
			catch (IOException ignored) {
			}
		}
		if (response.body() instanceof Flow.Publisher) {
			((Flow.Publisher<?>) response.body()).subscribe(new Flow.Subscriber<Object>() {

				public void onSubscribe(Flow.Subscription subscription) {
					subscription.cancel();
				}

				public void onNext(Object value) {
				}

				public void onError(Throwable error) {
				}

				public void onComplete() {
				}
			});
		}
	}

	private static Throwable timeoutError(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof org.apache.hc.client5.http.ConnectTimeoutException) {
				HttpConnectTimeoutException timeout = new HttpConnectTimeoutException(
						"HTTP connect timed out: " + cause.getMessage());
				timeout.addSuppressed(error);
				return timeout;
			}
			if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException) {
				HttpTimeoutException timeout = new HttpTimeoutException(
						"HTTP request timed out: " + cause.getMessage());
				timeout.addSuppressed(error);
				return timeout;
			}
		}
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) {
				return cause;
			}
		}
		return error;
	}

	@Override
	public synchronized void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		try {
			this.client.close(CloseMode.IMMEDIATE);
		}
		finally {
			if (this.alternate != null) {
				this.alternate.close();
			}
		}
	}

	/**
	 * Owns the exchange handle across the headers/body boundary and cancellation races.
	 *
	 * @author 芋道源码
	 */
	private static final class Exchange {

		private java.util.concurrent.Future<?> future;

		private boolean cancelled;

		private org.apache.hc.client5.http.async.AsyncExecRuntime runtime;

		synchronized void attach(java.util.concurrent.Future<?> value) {
			this.future = value;
			if (this.cancelled) {
				value.cancel(true);
				// A connection can be acquired before execute() returns its future.
				// Recheck ownership after attaching a request cancelled in that window.
				abortHttp1Endpoint();
			}
		}

		synchronized boolean attach(org.apache.hc.client5.http.async.AsyncExecRuntime value) {
			this.runtime = value;
			if (this.cancelled) {
				abortHttp1Endpoint();
				return false;
			}
			return true;
		}

		synchronized void cancel() {
			if (this.cancelled) {
				return;
			}
			this.cancelled = true;
			if (this.future != null) {
				this.future.cancel(true);
			}
			abortHttp1Endpoint();
		}

		private void abortHttp1Endpoint() {
			if (this.runtime != null) {
				org.apache.hc.client5.http.EndpointInfo endpoint = this.runtime.getEndpointInfo();
				// HTTP/1 cancellation must close a stalled socket instead of waiting to
				// drain its response. HTTP/2 uses the normal per-stream cancellation
				// path so that unrelated multiplexed requests keep their connection.
				if (endpoint == null || endpoint.getProtocol() == null || endpoint.getProtocol().getMajor() < 2) {
					this.runtime.discardEndpoint();
				}
			}
		}

	}

	public enum Version {

		HTTP_1_1, HTTP_2

	}

	public enum Redirect {

		NEVER, NORMAL, ALWAYS

	}

	/**
	 * @author 芋道源码
	 */
	public static final class Builder {

		private Duration connectTimeout;

		private SSLContext sslContext;

		private SSLParameters sslParameters;

		private ProxySelector proxy;

		private boolean proxyCaptured;

		private String proxyUsername;

		private char[] proxyPassword;

		private CookieHandler cookies;

		private Executor executor;

		private Version version = Version.HTTP_2;

		private Redirect redirects = Redirect.NEVER;

		public Builder() {
		}

		private Builder(Builder source) {
			this.connectTimeout = source.connectTimeout;
			this.sslContext = source.sslContext;
			if (source.sslParameters != null) {
				sslParameters(source.sslParameters);
			}
			this.proxy = source.proxyCaptured ? source.proxy : ProxySelector.getDefault();
			this.proxyCaptured = true;
			this.proxyUsername = source.proxyUsername;
			this.proxyPassword = source.proxyPassword == null ? null : source.proxyPassword.clone();
			this.cookies = source.cookies;
			this.executor = source.executor;
			this.version = source.version;
			this.redirects = source.redirects;
		}

		public Builder connectTimeout(Duration value) {
			if (value.isNegative() || value.isZero()) {
				throw new IllegalArgumentException("Connect timeout must be positive");
			}
			this.connectTimeout = value;
			return this;
		}

		public Builder sslContext(SSLContext value) {
			this.sslContext = Objects.requireNonNull(value);
			return this;
		}

		public Builder sslParameters(SSLParameters value) {
			Objects.requireNonNull(value);
			SSLParameters copy = new SSLParameters();
			copy.setProtocols(value.getProtocols());
			copy.setCipherSuites(value.getCipherSuites());
			copy.setAlgorithmConstraints(value.getAlgorithmConstraints());
			copy.setEndpointIdentificationAlgorithm(value.getEndpointIdentificationAlgorithm());
			copy.setServerNames(value.getServerNames());
			copy.setSNIMatchers(value.getSNIMatchers());
			copy.setUseCipherSuitesOrder(value.getUseCipherSuitesOrder());
			this.sslParameters = copy;
			return this;
		}

		public Builder proxy(ProxySelector value) {
			this.proxy = Objects.requireNonNull(value);
			this.proxyCaptured = true;
			return this;
		}

		/**
		 * Supplies credentials only for challenges from the selected HTTP proxy.
		 * @param username proxy username
		 * @param password proxy password, copied defensively
		 * @return this builder
		 * @since 2.0.0
		 */
		public Builder proxyCredentials(String username, char[] password) {
			this.proxyUsername = Objects.requireNonNull(username);
			this.proxyPassword = Objects.requireNonNull(password).clone();
			return this;
		}

		public Builder cookieHandler(CookieHandler value) {
			this.cookies = Objects.requireNonNull(value);
			return this;
		}

		public Builder executor(Executor value) {
			this.executor = Objects.requireNonNull(value);
			return this;
		}

		public Builder version(Version value) {
			this.version = Objects.requireNonNull(value);
			return this;
		}

		public Builder followRedirects(Redirect value) {
			this.redirects = Objects.requireNonNull(value);
			return this;
		}

		public HttpClient build() {
			return new HttpClient(this);
		}

	}

	/**
	 * @author 芋道源码
	 */
	private static final class Response<T> implements HttpResponse<T> {

		private final HttpRequest request;

		private final int status;

		private final HttpHeaders headers;

		private final T body;

		private final Version version;

		private final URI uri;

		private Response(HttpRequest request, int status, HttpHeaders headers, T body, Version version, URI uri) {
			this.request = request;
			this.status = status;
			this.headers = headers;
			this.body = body;
			this.version = version;
			this.uri = uri;
		}

		public int statusCode() {
			return this.status;
		}

		public HttpHeaders headers() {
			return this.headers;
		}

		public T body() {
			return this.body;
		}

		public Version version() {
			return this.version;
		}

		public URI uri() {
			return this.uri;
		}

		public HttpRequest request() {
			return this.request;
		}

	}

}
