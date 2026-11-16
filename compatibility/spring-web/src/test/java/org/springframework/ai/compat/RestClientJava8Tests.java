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

package org.springframework.ai.compat;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownHttpStatusCodeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real HTTP and resource-lifecycle contracts for the Spring 5 RestClient source port.
 *
 * @author 芋道源码
 */
class RestClientJava8Tests {

	private MockWebServer server;

	@BeforeEach
	void startServer() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
	}

	@AfterEach
	void stopServer() throws Exception {
		this.server.shutdown();
	}

	@Test
	void executesOnJava8() {
		assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
	}

	@Test
	void sendsJsonHeadersAndUriVariablesAndReadsResponse() throws Exception {
		this.server
			.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{\"answer\":42}"));
		RestClient client = RestClient.builder()
			.baseUrl(this.server.url("/v1").toString())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test")
			.build();
		Map<?, ?> result = client.post()
			.uri("/items/{id}", "a b")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Collections.singletonMap("question", "hello"))
			.retrieve()
			.body(Map.class);
		assertThat(result.get("answer")).isEqualTo(42);
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getPath()).isEqualTo("/v1/items/a%20b");
		assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test");
		assertThat(request.getBody().readUtf8()).isEqualTo("{\"question\":\"hello\"}");
	}

	@Test
	void preservesStandardAndUnknownErrorStatusAndBody() {
		RestClient client = RestClient.create(this.server.url("/").toString());
		this.server.enqueue(new MockResponse().setResponseCode(401).setBody("denied"));
		assertThatThrownBy(() -> client.get().retrieve().body(String.class))
			.isInstanceOfSatisfying(HttpClientErrorException.class, error -> {
				assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
				assertThat(error.getResponseBodyAsString()).isEqualTo("denied");
			});
		this.server.enqueue(new MockResponse().setResponseCode(599).setBody("try later"));
		assertThatThrownBy(() -> client.get().retrieve().body(String.class))
			.isInstanceOfSatisfying(UnknownHttpStatusCodeException.class, error -> {
				assertThat(error.getRawStatusCode()).isEqualTo(599);
				assertThat(error.getResponseBodyAsString()).isEqualTo("try later");
			});
	}

	@Test
	void customStatusHandlerReceivesUnknownNumericCodesBeforeDefaultHandler() {
		this.server.enqueue(new MockResponse().setResponseCode(599).setBody("provider failure"));
		AtomicInteger seen = new AtomicInteger();
		RestClient client = RestClient.create(this.server.url("/").toString());
		assertThatThrownBy(
				() -> client.get().retrieve().onStatus(status -> status.is5xxServerError(), (request, response) -> {
					seen.set(response.getRawStatusCode());
					throw new IllegalStateException("handled");
				}).body(String.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("handled");
		assertThat(seen).hasValue(599);
	}

	@Test
	void reusesRestTemplateInterceptorsExactlyOnce() {
		this.server.enqueue(new MockResponse().setBody("ok"));
		AtomicInteger calls = new AtomicInteger();
		RestTemplate template = new RestTemplate();
		template.getInterceptors().add((request, body, execution) -> {
			calls.incrementAndGet();
			request.getHeaders().set("X-Intercepted", "once");
			return execution.execute(request, body);
		});
		RestClient client = RestClient.create(template);
		assertThat(client.get().uri(this.server.url("/").uri()).retrieve().body(String.class)).isEqualTo("ok");
		assertThat(calls).hasValue(1);
	}

	@Test
	void builderAndMutateKeepDefaultHeaderSnapshotsIndependent() throws Exception {
		RestClient.Builder builder = RestClient.builder()
			.baseUrl(this.server.url("/").toString())
			.defaultHeader("X-Mode", "one");
		RestClient first = builder.build();
		RestClient second = builder.defaultHeader("X-Mode", "two").build();
		RestClient third = first.mutate().defaultHeader("X-Mode", "three").build();
		for (RestClient client : new RestClient[] { first, second, third }) {
			this.server.enqueue(new MockResponse().setBody("ok"));
			client.get().retrieve().toBodilessEntity();
		}
		assertThat(this.server.takeRequest().getHeader("X-Mode")).isEqualTo("one");
		assertThat(this.server.takeRequest().getHeader("X-Mode")).isEqualTo("two");
		assertThat(this.server.takeRequest().getHeader("X-Mode")).isEqualTo("three");
	}

	@Test
	void closesResponseOnSuccessfulAndFailedExchange() {
		for (boolean fail : new boolean[] { false, true }) {
			AtomicBoolean closed = new AtomicBoolean();
			MockClientHttpResponse response = new MockClientHttpResponse("ok".getBytes(StandardCharsets.UTF_8),
					HttpStatus.OK) {

				@Override
				public void close() {
					closed.set(true);
					super.close();
				}
			};
			RestClient client = RestClient.builder().requestFactory((uri, method) -> {
				MockClientHttpRequest request = new MockClientHttpRequest(method, uri);
				request.setResponse(response);
				return request;
			}).build();
			if (fail) {
				assertThatThrownBy(() -> client.get().uri("http://localhost/test").exchange((request, result) -> {
					throw new IllegalStateException("failed");
				})).isInstanceOf(IllegalStateException.class);
			}
			else {
				assertThat(client.get().uri("http://localhost/test").retrieve().body(String.class)).isEqualTo("ok");
			}
			assertThat(closed).isTrue();
		}
	}

	@Test
	void recordsHttpObservationWithUnexpandedUriTemplateAndStatus() {
		this.server.enqueue(new MockResponse().setBody("ok"));
		TestObservationRegistry registry = TestObservationRegistry.create();
		RestClient client = RestClient.builder()
			.baseUrl(this.server.url("/v1").toString())
			.observationRegistry(registry)
			.build();
		assertThat(client.get().uri("/items/{id}", "123").retrieve().body(String.class)).isEqualTo("ok");
		TestObservationRegistryAssert.assertThat(registry)
			.hasObservationWithNameEqualTo("http.client.requests")
			.that()
			.hasLowCardinalityKeyValue("uri", "/v1/items/{id}")
			.hasLowCardinalityKeyValue("status", "200")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void httpClient5AdapterKeepsSuppliedRedirectPolicy() throws Exception {
		this.server.enqueue(new MockResponse().setResponseCode(302)
			.setHeader("Location", this.server.url("/redirected").toString()));
		this.server.enqueue(new MockResponse().setBody("must not follow"));
		try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient http = org.apache.hc.client5.http.impl.classic.HttpClients
			.custom()
			.disableRedirectHandling()
			.build()) {
			RestClient client = RestClient.builder()
				.requestFactory(new org.springframework.ai.compat.http.HttpComponentsClientHttpRequestFactory(http))
				.build();
			assertThat(
					client.get().uri(this.server.url("/original").uri()).retrieve().toBodilessEntity().getStatusCode())
				.isEqualTo(HttpStatus.FOUND);
			assertThat(this.server.getRequestCount()).isEqualTo(1);
			assertThat(this.server.takeRequest().getPath()).isEqualTo("/original");
		}
	}

}
