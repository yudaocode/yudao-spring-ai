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
package cn.iocoder.ai.compatibility;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the standalone AWS starter dependency closure and local SigV4 signing.
 *
 * @author 芋道源码
 */
@Timeout(30)
class OpenSearchAwsStarterTest {

	@Test
	void awsStarterCreatesItsNativeClientWithExplicitLocalFixtureSettings() throws Exception {
		OpenSearchClient client = null;
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
			.web(WebApplicationType.NONE)
			.properties("spring.main.banner-mode=off", "logging.level.root=WARN",
					"spring.ai.vectorstore.opensearch.aws.enabled=true",
					"spring.ai.vectorstore.opensearch.aws.host=localhost",
					"spring.ai.vectorstore.opensearch.aws.service-name=es",
					"spring.ai.vectorstore.opensearch.aws.region=us-east-1",
					"spring.ai.vectorstore.opensearch.aws.access-key=sdk-test-access",
					"spring.ai.vectorstore.opensearch.aws.secret-key=sdk-test-secret",
					"spring.ai.vectorstore.opensearch.initialize-schema=false",
					"spring.ai.vectorstore.opensearch.dimensions=3")
			.run()) {
			client = context.getBean(OpenSearchClient.class);
			assertTrue(client._transport() instanceof AwsSdk2Transport);
			assertNotNull(context.getBean(OpenSearchVectorStore.class));
		}
		finally {
			if (client != null)
				client._transport().close();
		}
	}

	@Test
	void awsTransportSignsRequestsWithTheStarterRuntimeDependencies() throws Exception {
		SdkHttpClient http = mock(SdkHttpClient.class);
		when(http.clientName()).thenReturn("local-signing-fixture");
		ExecutableHttpRequest executable = mock(ExecutableHttpRequest.class);
		when(http.prepareRequest(any(HttpExecuteRequest.class))).thenReturn(executable);
		String response = "{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0},"
				+ "\"hits\":{\"total\":{\"value\":0,\"relation\":\"eq\"},\"hits\":[]}}";
		when(executable.call()).thenReturn(HttpExecuteResponse.builder()
			.response(SdkHttpResponse.builder().statusCode(200).putHeader("Content-Type", "application/json").build())
			.responseBody(AbortableInputStream
				.create(new java.io.ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8))))
			.build());
		AwsSdk2TransportOptions options = AwsSdk2TransportOptions.builder()
			.setCredentials(
					StaticCredentialsProvider.create(AwsBasicCredentials.create("sdk-test-access", "sdk-test-secret")))
			.build();
		try (AwsSdk2Transport transport = new AwsSdk2Transport(http, "localhost", "es", Region.US_EAST_1, options)) {
			new OpenSearchClient(transport)
				.search(search -> search.index("compat").query(query -> query.matchAll(match -> match)), Map.class);
			ArgumentCaptor<HttpExecuteRequest> request = ArgumentCaptor.forClass(HttpExecuteRequest.class);
			verify(http).prepareRequest(request.capture());
			assertTrue(request.getValue()
				.httpRequest()
				.firstMatchingHeader("Authorization")
				.get()
				.startsWith("AWS4-HMAC-SHA256 Credential=sdk-test-access/"));
			assertTrue(request.getValue().httpRequest().firstMatchingHeader("X-Amz-Date").isPresent());
		}
	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

		@Bean
		EmbeddingModel model() {
			EmbeddingModel model = mock(EmbeddingModel.class);
			when(model.embed(anyString())).thenReturn(new float[] { 0.25f, -0.5f, 1.0f });
			when(model.dimensions()).thenReturn(3);
			return model;
		}

	}

}
