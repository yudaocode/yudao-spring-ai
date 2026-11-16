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
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies proxy credentials against actual HTTP challenges and origin boundaries.
 *
 * @author 芋道源码
 */
@Timeout(15)
class HttpBridgeProxyAuthenticationTests {

	@Test
	void authenticatesOnlyAfterTheSelectedProxyChallenge() throws Exception {
		try (MockWebServer proxy = new MockWebServer()) {
			proxy.start();
			proxy.enqueue(new MockResponse().setResponseCode(407)
				.setHeader("Proxy-Authenticate", "Basic realm=\"sandbox-proxy\""));
			proxy.enqueue(new MockResponse().setBody("proxied"));
			Proxy selected = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", proxy.getPort()));
			try (HttpClient client = HttpClient.newBuilder()
				.proxy(selector(selected))
				.proxyCredentials("fixture-user", "fixture-password".toCharArray())
				.build()) {
				HttpResponse<String> response = client.send(request("http://sandbox.invalid/tool"),
						HttpResponse.BodyHandlers.ofString());
				assertEquals(200, response.statusCode());
				assertEquals("proxied", response.body());
				assertNull(proxy.takeRequest(5, TimeUnit.SECONDS).getHeader("Proxy-Authorization"));
				assertEquals(Credentials.basic("fixture-user", "fixture-password"),
						proxy.takeRequest(5, TimeUnit.SECONDS).getHeader("Proxy-Authorization"));
			}
		}
	}

	@Test
	void doesNotOfferProxyCredentialsToADirectOriginChallenge() throws Exception {
		try (MockWebServer origin = new MockWebServer()) {
			origin.start();
			origin.enqueue(
					new MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=\"origin\""));
			try (HttpClient client = HttpClient.newBuilder()
				.proxy(selector(Proxy.NO_PROXY))
				.proxyCredentials("fixture-user", "fixture-password".toCharArray())
				.build()) {
				assertEquals(401,
						client.send(request(origin.url("/private").toString()), HttpResponse.BodyHandlers.ofString())
							.statusCode());
				okhttp3.mockwebserver.RecordedRequest sent = origin.takeRequest(5, TimeUnit.SECONDS);
				assertNull(sent.getHeader("Authorization"));
				assertNull(sent.getHeader("Proxy-Authorization"));
				assertEquals(1, origin.getRequestCount());
			}
		}
	}

	@Test
	void doesNotOfferProxyCredentialsToAnOriginChallengeThroughTheProxy() throws Exception {
		try (MockWebServer proxy = new MockWebServer()) {
			proxy.start();
			proxy.enqueue(
					new MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=\"origin\""));
			Proxy selected = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", proxy.getPort()));
			try (HttpClient client = HttpClient.newBuilder()
				.proxy(selector(selected))
				.proxyCredentials("fixture-user", "fixture-password".toCharArray())
				.build()) {
				assertEquals(401,
						client.send(request("http://sandbox.invalid/private"), HttpResponse.BodyHandlers.ofString())
							.statusCode());
				okhttp3.mockwebserver.RecordedRequest sent = proxy.takeRequest(5, TimeUnit.SECONDS);
				assertNull(sent.getHeader("Authorization"));
				assertNull(sent.getHeader("Proxy-Authorization"));
				assertEquals(1, proxy.getRequestCount());
			}
		}
	}

	private static HttpRequest request(String uri) {
		return HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(5)).build();
	}

	private static ProxySelector selector(Proxy selected) {
		return new ProxySelector() {
			@Override
			public List<Proxy> select(URI uri) {
				return Collections.singletonList(selected);
			}

			@Override
			public void connectFailed(URI uri, SocketAddress address, IOException error) {
			}
		};
	}

}
