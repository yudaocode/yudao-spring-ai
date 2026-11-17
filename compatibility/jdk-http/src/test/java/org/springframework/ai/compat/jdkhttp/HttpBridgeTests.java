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

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author 芋道源码
 */
@Timeout(15)
class HttpBridgeTests {

	private MockWebServer server;

	@BeforeEach
	void start() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
	}

	@AfterEach
	void stop() throws Exception {
		this.server.shutdown();
	}

	@Test
	void runsOnJava8() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
	}

	@Test
	void sendsBodyAndHeadersAndKeepsBuilderCopiesIndependent() throws Exception {
		this.server.enqueue(new MockResponse().setBody("ok"));
		HttpRequest.Builder original = HttpRequest.newBuilder(this.server.url("/rpc").uri()).header("X-Mode", "first");
		HttpRequest request = original.copy()
			.setHeader("X-Mode", "second")
			.POST(HttpRequest.BodyPublishers.ofString("request"))
			.build();
		assertEquals("first", original.build().headers().firstValue("x-mode").get());
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.get(5, TimeUnit.SECONDS);
			assertEquals("ok", response.body());
			assertEquals(200, response.statusCode());
			okhttp3.mockwebserver.RecordedRequest sent = this.server.takeRequest();
			assertEquals("request", sent.getBody().readUtf8());
			assertEquals("second", sent.getHeader("X-Mode"));
		}
	}

	@Test
	void overridesClientProtocolWithoutLosingBodyOrReusingMutableBuilder() throws Exception {
		this.server.enqueue(new MockResponse().setBody("override"));
		HttpClient.Builder builder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2);
		HttpClient client = builder.build();
		builder.proxy(new ProxySelector() {
			public List<Proxy> select(URI uri) {
				throw new AssertionError("Client must retain the original configuration");
			}

			public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) {
			}
		});
		try {
			HttpRequest request = HttpRequest.newBuilder(this.server.url("/override").uri())
				.version(HttpClient.Version.HTTP_1_1)
				.POST(HttpRequest.BodyPublishers.ofString("payload"))
				.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			assertEquals("override", response.body());
			assertEquals(HttpClient.Version.HTTP_1_1, response.version());
			okhttp3.mockwebserver.RecordedRequest sent = this.server.takeRequest(2, TimeUnit.SECONDS);
			assertEquals("payload", sent.getBody().readUtf8());
			assertEquals(null, sent.getHeader("Upgrade"));
			client.close();
			client.close();
			ExecutionException error = assertThrows(ExecutionException.class,
					() -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(2, TimeUnit.SECONDS));
			assertTrue(error.getCause() instanceof java.io.IOException);
			assertEquals(1, this.server.getRequestCount());
		}
		finally {
			client.close();
		}
	}

	@Test
	void allowsRequestToPreferHttp2AndFallsBackToServerHttp1() throws Exception {
		this.server.enqueue(new MockResponse().setBody("negotiated"));
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<String> response = client.send(HttpRequest.newBuilder(this.server.url("/prefer2").uri())
				.version(HttpClient.Version.HTTP_2)
				.build(), HttpResponse.BodyHandlers.ofString());
			assertEquals("negotiated", response.body());
			assertEquals(HttpClient.Version.HTTP_1_1, response.version());
		}
	}

	@Test
	void returnsHeadersBeforeBodyAndHonorsDemandAndCancellation() throws Exception {
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream")
			.setBody("data: first\n\ndata: second\n\n")
			.setBodyDelay(500, TimeUnit.MILLISECONDS));
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<Flow.Publisher<List<ByteBuffer>>> response = client
				.sendAsync(HttpRequest.newBuilder(this.server.url("/sse").uri()).build(),
						HttpResponse.BodyHandlers.ofPublisher())
				.get(2, TimeUnit.SECONDS);
			AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
			CountDownLatch subscribed = new CountDownLatch(1);
			CountDownLatch received = new CountDownLatch(1);
			AtomicInteger chunks = new AtomicInteger();
			response.body().subscribe(new Flow.Subscriber<List<ByteBuffer>>() {

				public void onSubscribe(Flow.Subscription value) {
					subscription.set(value);
					subscribed.countDown();
				}

				public void onNext(List<ByteBuffer> value) {
					chunks.incrementAndGet();
					received.countDown();
					subscription.get().cancel();
				}

				public void onError(Throwable error) {
					received.countDown();
				}

				public void onComplete() {
				}
			});
			assertTrue(subscribed.await(1, TimeUnit.SECONDS));
			assertFalse(received.await(700, TimeUnit.MILLISECONDS));
			subscription.get().request(1);
			assertTrue(received.await(2, TimeUnit.SECONDS));
			assertEquals(1, chunks.get());
			this.server.enqueue(new MockResponse().setBody("next"));
			assertEquals("next",
					client
						.sendAsync(HttpRequest.newBuilder(this.server.url("/next").uri()).build(),
								HttpResponse.BodyHandlers.ofString())
						.get(3, TimeUnit.SECONDS)
						.body());
		}
	}

	@Test
	void requestTimeoutCoversDelayedHeadersAndBody() throws Exception {
		for (boolean headers : new boolean[] { true, false }) {
			MockResponse response = new MockResponse().setBody("late");
			if (headers) {
				response.setHeadersDelay(1, TimeUnit.SECONDS);
			}
			else {
				response.setBodyDelay(1, TimeUnit.SECONDS);
			}
			this.server.enqueue(response);
			try (HttpClient client = HttpClient.newHttpClient()) {
				CompletableFuture<HttpResponse<String>> future = client
					.sendAsync(HttpRequest.newBuilder(this.server.url("/timeout").uri())
						.timeout(Duration.ofMillis(100))
						.build(), HttpResponse.BodyHandlers.ofString());
				ExecutionException error = assertThrows(ExecutionException.class,
						() -> future.get(2, TimeUnit.SECONDS));
				assertTrue(error.getCause() instanceof HttpTimeoutException, error.toString());
				assertNull(error.getCause().getCause());
				assertTrue(error.getCause().getMessage().contains("timed out"));
			}
		}
	}

	@Test
	void cancelsPendingRequest() throws Exception {
		this.server.enqueue(new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS).setBody("late"));
		try (HttpClient client = HttpClient.newHttpClient()) {
			CompletableFuture<HttpResponse<String>> future = client.sendAsync(
					HttpRequest.newBuilder(this.server.url("/cancel").uri()).build(),
					HttpResponse.BodyHandlers.ofString());
			assertNotNull(this.server.takeRequest(2, TimeUnit.SECONDS));
			assertTrue(future.cancel(true));
			assertTrue(future.isCancelled());
		}
	}

	@Test
	void doesNotRedirectUnlessEnabled() throws Exception {
		for (HttpClient.Redirect policy : new HttpClient.Redirect[] { HttpClient.Redirect.NEVER,
				HttpClient.Redirect.NORMAL }) {
			this.server.enqueue(new MockResponse().setResponseCode(302)
				.setHeader("Location", this.server.url("/redirected").toString()));
			if (policy != HttpClient.Redirect.NEVER) {
				this.server.enqueue(new MockResponse().setBody("followed"));
			}
			try (HttpClient client = HttpClient.newBuilder().followRedirects(policy).build()) {
				HttpResponse<String> response = client
					.sendAsync(HttpRequest.newBuilder(this.server.url("/start").uri()).build(),
							HttpResponse.BodyHandlers.ofString())
					.get(3, TimeUnit.SECONDS);
				assertEquals(policy == HttpClient.Redirect.NEVER ? 302 : 200, response.statusCode());
			}
		}
	}

	@Test
	void honorsExplicitHttpProxy() throws Exception {
		this.server.enqueue(new MockResponse().setBody("proxied"));
		ProxySelector selector = new ProxySelector() {

			public List<Proxy> select(URI uri) {
				return Collections
					.singletonList(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", server.getPort())));
			}

			public void connectFailed(URI uri, SocketAddress address, java.io.IOException error) {
			}
		};
		try (HttpClient client = HttpClient.newBuilder().proxy(selector).build()) {
			HttpResponse<String> response = client
				.sendAsync(HttpRequest.newBuilder(URI.create("http://nonexistent.invalid/rpc")).build(),
						HttpResponse.BodyHandlers.ofString())
				.get(3, TimeUnit.SECONDS);
			assertEquals("proxied", response.body());
			assertTrue(this.server.takeRequest().getRequestLine().contains("http://nonexistent.invalid/rpc"));
		}
	}

	@Test
	void honorsCookieHandlerAndCompletionExecutor() throws Exception {
		this.server.enqueue(new MockResponse().setHeader("Set-Cookie", "session=one; Path=/").setBody("first"));
		this.server.enqueue(new MockResponse().setBody("second").setBodyDelay(100, TimeUnit.MILLISECONDS));
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
			.newSingleThreadExecutor(task -> new Thread(task, "chosen-completion"));
		try (HttpClient client = HttpClient.newBuilder()
			.cookieHandler(new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL))
			.executor(executor)
			.build()) {
			HttpRequest request = HttpRequest.newBuilder(this.server.url("/cookie").uri()).build();
			client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(3, TimeUnit.SECONDS);
			AtomicReference<String> completionThread = new AtomicReference<>();
			CompletableFuture<HttpResponse<String>> result = client.sendAsync(request,
					HttpResponse.BodyHandlers.ofString());
			result.whenComplete((response, error) -> completionThread.set(Thread.currentThread().getName()))
				.get(3, TimeUnit.SECONDS);
			assertEquals("chosen-completion", completionThread.get());
			this.server.takeRequest();
			assertTrue(this.server.takeRequest().getHeader("Cookie").contains("session=one"));
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void honorsCustomTlsContextAndParametersAndRejectsUntrustedCertificate() throws Exception {
		java.security.KeyStore store = java.security.KeyStore.getInstance("JKS");
		try (java.io.InputStream input = getClass().getResourceAsStream("/localhost-test.jks")) {
			store.load(input, "test-only".toCharArray());
		}
		javax.net.ssl.KeyManagerFactory keys = javax.net.ssl.KeyManagerFactory
			.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
		keys.init(store, "test-only".toCharArray());
		javax.net.ssl.TrustManagerFactory trust = javax.net.ssl.TrustManagerFactory
			.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
		trust.init(store);
		javax.net.ssl.SSLContext tls = javax.net.ssl.SSLContext.getInstance("TLS");
		tls.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
		this.server.useHttps(tls.getSocketFactory(), false);
		this.server.enqueue(new MockResponse().setBody("secure"));
		HttpRequest request = HttpRequest.newBuilder(this.server.url("/secure").uri()).build();
		try (HttpClient client = HttpClient.newHttpClient()) {
			assertThrows(ExecutionException.class,
					() -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(3, TimeUnit.SECONDS));
		}
		javax.net.ssl.SSLParameters parameters = new javax.net.ssl.SSLParameters();
		parameters.setProtocols(new String[] { "TLSv1.2" });
		try (HttpClient client = HttpClient.newBuilder().sslContext(tls).sslParameters(parameters).build()) {
			assertEquals("secure",
					client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).get(3, TimeUnit.SECONDS).body());
			assertEquals(okhttp3.TlsVersion.TLS_1_2,
					this.server.takeRequest(2, TimeUnit.SECONDS).getHandshake().tlsVersion());
		}
	}

	@Test
	void supportsStreamingRequestAndResponseAndClosesRequestStream() throws Exception {
		java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
		this.server.enqueue(new MockResponse().setBody("streamed response"));
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder(this.server.url("/stream").uri())
				.POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(
						"streamed request".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {

					@Override
					public void close() throws java.io.IOException {
						closed.set(true);
						super.close();
					}
				}))
				.build();
			HttpResponse<java.io.InputStream> response = client.send(request,
					HttpResponse.BodyHandlers.ofInputStream());
			try (java.io.InputStream body = response.body()) {
				java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
				byte[] buffer = new byte[3];
				int count;
				while ((count = body.read(buffer)) != -1) {
					out.write(buffer, 0, count);
				}
				assertEquals("streamed response",
						new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
			}
			assertEquals("streamed request", this.server.takeRequest().getBody().readUtf8());
			assertTrue(closed.get());
		}
	}

	@Test
	void closingResponseInputStreamCancelsBlockedRead() throws Exception {
		CountDownLatch subscribed = new CountDownLatch(1);
		CountDownLatch cancelled = new CountDownLatch(1);
		PublisherInputStream input = new PublisherInputStream(
				FlowAdapters.publisherToFlowPublisher(reactor.core.publisher.Flux.<List<ByteBuffer>>never()
					.doOnSubscribe(subscription -> subscribed.countDown())
					.doOnCancel(cancelled::countDown)));
		java.util.concurrent.ExecutorService reader = java.util.concurrent.Executors.newSingleThreadExecutor();
		try {
			java.util.concurrent.Future<Integer> read = reader.submit(() -> input.read());
			assertTrue(subscribed.await(2, TimeUnit.SECONDS));
			input.close();
			assertTrue(cancelled.await(2, TimeUnit.SECONDS));
			assertThrows(ExecutionException.class, () -> read.get(2, TimeUnit.SECONDS));
		}
		finally {
			input.close();
			reader.shutdownNow();
		}
	}

	@Test
	void exposesConnectionFailuresAsIOException() throws Exception {
		java.net.URI uri;
		try (MockWebServer closed = new MockWebServer()) {
			closed.start();
			uri = closed.url("/closed").uri();
		}
		try (HttpClient client = HttpClient.newHttpClient()) {
			ExecutionException failure = assertThrows(ExecutionException.class,
					() -> client.sendAsync(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString())
						.get(3, TimeUnit.SECONDS));
			assertTrue(failure.getCause() instanceof java.io.IOException, failure.toString());
		}
	}

	@org.junit.jupiter.api.Test
	void exposesConfiguredAndAbsentConnectTimeout() {
		try (HttpClient client = HttpClient.newHttpClient()) {
			org.junit.jupiter.api.Assertions.assertFalse(client.connectTimeout().isPresent());
		}
		try (HttpClient client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(2)).build()) {
			org.junit.jupiter.api.Assertions.assertEquals(java.time.Duration.ofSeconds(2),
					client.connectTimeout().get());
		}
	}

	@org.junit.jupiter.api.Test
	void preservesHeaderCaseAndCaseInsensitiveEqualityAndLookup() {
		HttpHeaders upper = HttpHeaders.of(
				java.util.Collections.singletonMap("X-Test", java.util.Collections.singletonList("one")),
				(name, value) -> true);
		HttpHeaders lower = HttpHeaders.of(
				java.util.Collections.singletonMap("x-test", java.util.Collections.singletonList("one")),
				(name, value) -> true);
		org.junit.jupiter.api.Assertions.assertEquals("X-Test", upper.map().keySet().iterator().next());
		org.junit.jupiter.api.Assertions.assertEquals(java.util.Collections.singletonList("one"),
				upper.map().get("x-test"));
		org.junit.jupiter.api.Assertions.assertEquals(upper, lower);
		org.junit.jupiter.api.Assertions.assertEquals(upper.hashCode(), lower.hashCode());
	}

	@Test
	void exposesIndependentProtocolAndRedirectConfiguration() {
		HttpClient.Builder builder = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_1_1)
			.followRedirects(HttpClient.Redirect.ALWAYS);
		try (HttpClient client = builder.build(); HttpClient defaults = HttpClient.newHttpClient()) {
			builder.version(HttpClient.Version.HTTP_2).followRedirects(HttpClient.Redirect.NEVER);
			assertEquals(HttpClient.Version.HTTP_1_1, client.version());
			assertEquals(HttpClient.Redirect.ALWAYS, client.followRedirects());
			// Match the JDK client default: prefer HTTP/2 and allow HTTP/1.1 fallback.
			assertEquals(HttpClient.Version.HTTP_2, defaults.version());
			assertEquals(HttpClient.Redirect.NEVER, defaults.followRedirects());
		}
	}

	@Test
	void drainsDiscardedBodiesAndCanSendAnotherRequest() throws Exception {
		this.server.enqueue(new MockResponse().setBody(new String(new char[256 * 1024]).replace('\0', 'x')));
		this.server.enqueue(new MockResponse().setBody("next"));
		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpResponse<Void> discarded = client.send(
					HttpRequest.newBuilder(this.server.url("/discard").uri()).build(),
					HttpResponse.BodyHandlers.discarding());
			assertEquals(200, discarded.statusCode());
			assertNull(discarded.body());
			HttpResponse<String> next = client.send(HttpRequest.newBuilder(this.server.url("/next").uri()).build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals("next", next.body());
			assertEquals(2, this.server.getRequestCount());
		}
	}

}
