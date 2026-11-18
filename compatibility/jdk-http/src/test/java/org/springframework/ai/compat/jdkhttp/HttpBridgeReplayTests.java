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

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import okhttp3.Credentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises replay and cookie handling across actual authentication and redirect hops.
 *
 * @author 芋道源码
 */
@Timeout(15)
class HttpBridgeReplayTests {

	@ParameterizedTest
	@ValueSource(ints = { 307, 308 })
	void followsBodylessRedirectFromJdkHttpServer(int status) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		AtomicInteger redirectRequests = new AtomicInteger();
		AtomicInteger completedRequests = new AtomicInteger();
		server.createContext("/", exchange -> {
			try {
				if ("/files/folder%2Fdata".equals(exchange.getRequestURI().getRawPath())) {
					redirectRequests.incrementAndGet();
					exchange.getResponseHeaders().set("Location", "/files/folder/data");
					exchange.sendResponseHeaders(status, -1);
					return;
				}
				if (!"/files/folder/data".equals(exchange.getRequestURI().getRawPath())) {
					exchange.sendResponseHeaders(404, -1);
					return;
				}
				completedRequests.incrementAndGet();
				byte[] body = "downloaded".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream output = exchange.getResponseBody()) {
					output.write(body);
				}
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		URI root = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
		try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
			HttpResponse<String> response = client.sendAsync(
					HttpRequest.newBuilder(root.resolve("/files/folder%2Fdata")).GET().build(),
					HttpResponse.BodyHandlers.ofString()).get(5, TimeUnit.SECONDS);
			assertEquals(200, response.statusCode());
			assertEquals(root.resolve("/files/folder/data"), response.uri());
			assertEquals("downloaded", response.body());
			assertEquals(1, redirectRequests.get());
			assertEquals(1, completedRequests.get());
		}
		finally {
			server.stop(0);
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 307, 308 })
	void replaysByteBodyAfterRedirect(int status) throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.start();
			server.enqueue(new MockResponse().setResponseCode(status).setHeader("Location", "/final"));
			server.enqueue(new MockResponse().setBody("done"));
			try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()) {
				HttpResponse<String> response = client.send(
						request(server.url("/first").uri(), HttpRequest.BodyPublishers.ofString("完整正文")),
						HttpResponse.BodyHandlers.ofString());
				assertEquals(200, response.statusCode());
				assertEquals(server.url("/final").uri(), response.uri());
				assertEquals("完整正文", server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8());
				assertEquals("完整正文", server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8());
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 307, 308, 407 })
	void opensFreshInputStreamForEveryTransmission(int status) throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.start();
			server.enqueue(new MockResponse().setResponseCode(status)
				.setHeader(status == 407 ? "Proxy-Authenticate" : "Location",
						status == 407 ? "Basic realm=\"fixture\"" : "/final"));
			server.enqueue(new MockResponse().setBody("done"));
			HttpClient.Builder builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS);
			URI uri = server.url("/first").uri();
			if (status == 407) {
				builder.proxy(new ProxySelector() {
					@Override
					public List<Proxy> select(URI target) {
						return Collections.singletonList(
								new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", server.getPort())));
					}

					@Override
					public void connectFailed(URI target, SocketAddress address, java.io.IOException error) {
					}
				}).proxyCredentials("fixture", "secret".toCharArray());
				uri = URI.create("http://fixture.invalid/first");
			}
			AtomicInteger opened = new AtomicInteger();
			AtomicInteger closed = new AtomicInteger();
			HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofInputStream(() -> {
				opened.incrementAndGet();
				return new ByteArrayInputStream("complete-body".getBytes(StandardCharsets.UTF_8)) {
					@Override
					public void close() {
						closed.incrementAndGet();
					}
				};
			});
			try (HttpClient client = builder.build()) {
				assertEquals(200, client.send(request(uri, body), HttpResponse.BodyHandlers.ofString()).statusCode());
				RecordedRequest first = server.takeRequest(2, TimeUnit.SECONDS);
				RecordedRequest second = server.takeRequest(2, TimeUnit.SECONDS);
				assertEquals("complete-body", first.getBody().readUtf8());
				assertEquals("complete-body", second.getBody().readUtf8());
				assertEquals("POST", second.getMethod());
				if (status == 407) {
					assertNull(first.getHeader("Proxy-Authorization"));
					assertEquals(Credentials.basic("fixture", "secret"), second.getHeader("Proxy-Authorization"));
				}
				assertEquals(2, opened.get());
				assertEquals(2, closed.get());
			}
		}
	}

	@Test
	void preservesOpaqueContentTypeWithoutParsingCharset() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.start();
			server.enqueue(new MockResponse().setBody("done"));
			try (HttpClient client = HttpClient.newHttpClient()) {
				assertEquals(200,
						client
							.sendAsync(HttpRequest.newBuilder(server.url("/").uri())
								.header("Content-Type", "application/json; charset=no-such-charset")
								.POST(HttpRequest.BodyPublishers.ofString("{}"))
								.build(), HttpResponse.BodyHandlers.ofString())
							.get(3, TimeUnit.SECONDS)
							.statusCode());
				RecordedRequest sent = server.takeRequest(2, TimeUnit.SECONDS);
				assertEquals("application/json; charset=no-such-charset", sent.getHeader("Content-Type"));
				assertEquals("{}", sent.getBody().readUtf8());
			}
		}
	}

	@Test
	void storesAndSelectsCookiesOnEverySameHostRedirect() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.start();
			server.enqueue(new MockResponse().setResponseCode(302)
				.setHeader("Location", "/final")
				.setHeader("Set-Cookie", "redirect=one; Path=/"));
			server.enqueue(new MockResponse().setBody("done").setHeader("Set-Cookie", "final=two; Path=/"));
			CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
			try (HttpClient client = HttpClient.newBuilder()
				.cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.ALWAYS)
				.build()) {
				client.send(HttpRequest.newBuilder(server.url("/first").uri()).build(),
						HttpResponse.BodyHandlers.ofString());
				server.takeRequest(2, TimeUnit.SECONDS);
				assertTrue(server.takeRequest(2, TimeUnit.SECONDS).getHeader("Cookie").contains("redirect=one"));
				assertEquals(2, cookies.getCookieStore().getCookies().size());
			}
		}
	}

	@Test
	void attributesRedirectCookiesToTheirActualHost() throws Exception {
		try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer()) {
			first.start();
			second.start();
			URI target = URI.create(second.url("/final").toString().replace("localhost", "127.0.0.1"));
			first.enqueue(new MockResponse().setResponseCode(302)
				.setHeader("Location", target.toString())
				.setHeader("Set-Cookie", "first=one; Path=/"));
			second.enqueue(new MockResponse().setBody("done").setHeader("Set-Cookie", "second=two; Path=/"));
			CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
			try (HttpClient client = HttpClient.newBuilder()
				.cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.ALWAYS)
				.build()) {
				assertEquals(target,
						client
							.send(HttpRequest.newBuilder(first.url("/first").uri()).build(),
									HttpResponse.BodyHandlers.ofString())
							.uri());
				assertNull(second.takeRequest(2, TimeUnit.SECONDS).getHeader("Cookie"));
				assertTrue(cookies.get(first.url("/").uri(), Collections.emptyMap()).toString().contains("first=one"));
				assertFalse(
						cookies.get(first.url("/").uri(), Collections.emptyMap()).toString().contains("second=two"));
				assertTrue(cookies.get(target, Collections.emptyMap()).toString().contains("second=two"));
				assertFalse(cookies.get(target, Collections.emptyMap()).toString().contains("first=one"));
			}
		}
	}

	private static HttpRequest request(URI uri, HttpRequest.BodyPublisher body) {
		return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).POST(body).build();
	}

}
