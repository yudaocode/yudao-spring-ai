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

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.HttpComponentsClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Asynchronous Java 8 HTTP bridge for MCP's JDK HTTP transport surface. Uses Spring 5 and
 * HttpClient 5; response bodies remain demand-driven and cancellable.
 *
 * @author 芋道源码
 */
public final class HttpClient implements AutoCloseable {

	private static final AtomicInteger THREAD_IDS = new AtomicInteger();

	private final CloseableHttpAsyncClient client;

	private final CookieHandler cookies;

	private final Executor executor;

	private final Redirect redirects;

	private final Version version;

	private final Duration connectTimeout;

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
		final SSLParameters supplied = builder.sslParameters;
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
		ProxySelector proxy = builder.proxy == null ? ProxySelector.getDefault() : builder.proxy;
		if (proxy != null) {
			http.setRoutePlanner(new SystemDefaultRoutePlanner(proxy));
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
		this.client = http.build();
		this.client.start();
	}

	public java.util.Optional<Duration> connectTimeout() {
		return java.util.Optional.ofNullable(this.connectTimeout);
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
		if (request.version().isPresent() && request.version().get() != this.version) {
			throw new IllegalArgumentException("Per-request HTTP version must match the client version");
		}
		CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
		HttpClientContext context = HttpClientContext.create();
		context.setRequestConfig(RequestConfig.custom()
			.setRedirectsEnabled(this.redirects != Redirect.NEVER)
			.setExpectContinueEnabled(request.expectContinue())
			.build());
		HttpComponentsClientHttpConnector connector = new HttpComponentsClientHttpConnector(this.client,
				(method, uri) -> context);
		WebClient web = WebClient.builder().clientConnector(connector).build();
		WebClient.RequestBodySpec spec = web.method(HttpMethod.valueOf(request.method())).uri(request.uri());
		request.headers().map().forEach((name, values) -> values.forEach(value -> spec.header(name, value)));
		try {
			if (this.cookies != null) {
				this.cookies.get(request.uri(), request.headers().map())
					.forEach((name, values) -> values.forEach(value -> spec.header(name, value)));
			}
		}
		catch (IOException error) {
			future.completeExceptionally(error);
			return future;
		}
		if (request.bodyPublisher().isPresent()) {
			HttpRequest.BodyPublisher body = request.bodyPublisher().get();
			if (body.contentLength() >= 0) {
				spec.contentLength(body.contentLength());
			}
			Flux<DataBuffer> bytes = FlowAdapters.flowPublisherToFlux(body).map(buffer -> {
				ByteBuffer copy = buffer.duplicate();
				byte[] values = new byte[copy.remaining()];
				copy.get(values);
				return DefaultDataBufferFactory.sharedInstance.wrap(values);
			});
			spec.body(BodyInserters.fromDataBuffers(bytes));
		}
		// exchangeToMono releases an unconsumed body when its callback finishes. The
		// SDK receives headers first, then subscribes separately, so retain the response.
		@SuppressWarnings("deprecation")
		Mono<ClientResponse> exchange = spec.exchange();
		Mono<HttpResponse<T>> result = exchange.flatMap(response -> {
			try {
				if (this.cookies != null) {
					this.cookies.put(request.uri(), response.headers().asHttpHeaders());
				}
				Flux<List<ByteBuffer>> chunks = response.bodyToFlux(DataBuffer.class).map(buffer -> {
					try {
						byte[] bytes = new byte[buffer.readableByteCount()];
						buffer.read(bytes);
						return Collections.singletonList(ByteBuffer.wrap(bytes).asReadOnlyBuffer());
					}
					finally {
						DataBufferUtils.release(buffer);
					}
				}).doOnDiscard(DataBuffer.class, DataBufferUtils::release);
				Flow.Publisher<List<ByteBuffer>> body = FlowAdapters.publisherToFlowPublisher(chunks);
				HttpHeaders headers = HttpHeaders.of(response.headers().asHttpHeaders(), (name, entry) -> true);
				Version protocol = context.getProtocolVersion() != null && context.getProtocolVersion().getMajor() >= 2
						? Version.HTTP_2 : Version.HTTP_1_1;
				return Mono.fromCompletionStage(handler.apply(body))
					.<HttpResponse<T>>map(
							value -> new Response<>(request, response.rawStatusCode(), headers, value, protocol))
					.defaultIfEmpty(new Response<>(request, response.rawStatusCode(), headers, null, protocol))
					.doOnCancel(() -> release(response))
					.doOnError(error -> release(response));
			}
			catch (Throwable error) {
				release(response);
				return Mono.error(error);
			}
		}).doOnDiscard(ClientResponse.class, HttpClient::release).doOnDiscard(HttpResponse.class, HttpClient::discard);
		if (this.executor != null) {
			result = result.publishOn(Schedulers.fromExecutor(this.executor));
		}
		if (request.timeout().isPresent()) {
			result = result.timeout(request.timeout().get());
		}
		return result.onErrorMap(HttpClient::timeoutError).toFuture();
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
				HttpConnectTimeoutException timeout = new HttpConnectTimeoutException(cause.getMessage());
				timeout.initCause(error);
				return timeout;
			}
			if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException) {
				HttpTimeoutException timeout = new HttpTimeoutException(cause.getMessage());
				timeout.initCause(error);
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

	private static void release(ClientResponse response) {
		// Cancelling at onSubscribe aborts the body; do not drain an unbounded SSE
		// stream.
		response.bodyToFlux(DataBuffer.class).subscribe(new org.reactivestreams.Subscriber<DataBuffer>() {

			public void onSubscribe(org.reactivestreams.Subscription subscription) {
				subscription.cancel();
			}

			public void onNext(DataBuffer value) {
				DataBufferUtils.release(value);
			}

			public void onError(Throwable error) {
			}

			public void onComplete() {
			}
		});
	}

	@Override
	public void close() {
		this.client.close(CloseMode.IMMEDIATE);
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

		private CookieHandler cookies;

		private Executor executor;

		private Version version = Version.HTTP_1_1;

		private Redirect redirects = Redirect.NEVER;

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

		private Response(HttpRequest request, int status, HttpHeaders headers, T body, Version version) {
			this.request = request;
			this.status = status;
			this.headers = headers;
			this.body = body;
			this.version = version;
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
			return this.request.uri();
		}

		public HttpRequest request() {
			return this.request;
		}

	}

}
