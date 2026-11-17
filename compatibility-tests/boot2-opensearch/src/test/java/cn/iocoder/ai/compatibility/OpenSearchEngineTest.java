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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks vector score and threshold semantics against an explicitly supplied engine.
 *
 * @author 芋道源码
 */
@Timeout(60)
@EnabledIfSystemProperty(named = "compat.opensearch.engine.tests", matches = "true")
class OpenSearchEngineTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void cosineScoreAndThresholdAgreeWithTheActual219Engine(boolean approximate) throws Exception {
		OpenSearchClient client = new OpenSearchClient(ApacheHttpClient5TransportBuilder
			.builder(HttpHost.create(System.getProperty("compat.opensearch.endpoint")))
			.setMapper(new JacksonJsonpMapper())
			.build());
		String index = "spring-ai-score-" + UUID.randomUUID().toString();
		try {
			EmbeddingModel model = mock(EmbeddingModel.class);
			when(model.dimensions()).thenReturn(2);
			when(model.embed("identical")).thenReturn(new float[] { 1, 0 });
			new org.springframework.boot.test.context.runner.ApplicationContextRunner()
				.withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
					.of(org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchVectorStoreAutoConfiguration.class))
				.withBean(OpenSearchClient.class, () -> client)
				.withBean(EmbeddingModel.class, () -> model)
				.withPropertyValues("spring.ai.vectorstore.opensearch.aws.enabled=false",
						"spring.ai.vectorstore.opensearch.index-name=" + index,
						"spring.ai.vectorstore.opensearch.dimensions=2",
						"spring.ai.vectorstore.opensearch.initialize-schema=true",
						"spring.ai.vectorstore.opensearch.use-approximate-knn=" + approximate,
						"spring.ai.vectorstore.opensearch.cosine-similarity-score-boost=1.0")
				.run(context -> {
					org.junit.jupiter.api.Assertions.assertNull(context.getStartupFailure());
					OpenSearchVectorStore store = context.getBean(OpenSearchVectorStore.class);
					try {
						Map<String, Object> doc = new HashMap<>();
						doc.put("id", "same");
						doc.put("content", "预算 café 你好");
						doc.put("metadata", Collections.emptyMap());
						doc.put("embedding", Arrays.asList(1.0f, 0.0f));
						client.index(request -> request.index(index).id("same").document(doc));
						client.indices().refresh(request -> request.index(index));
						List<Document> found = store.similaritySearch(
								SearchRequest.builder().query("identical").topK(1).similarityThreshold(0).build());
						assertEquals(1, found.size());
						assertEquals(1.0, found.get(0).getScore(), 0.000001);
						assertEquals("预算 café 你好", found.get(0).getText());
						assertEquals(1, store
							.similaritySearch(
									SearchRequest.builder().query("identical").topK(1).similarityThreshold(0.9).build())
							.size());
					}
					catch (Exception ex) {
						throw new RuntimeException(ex);
					}
				});
		}
		finally {
			try {
				if (client.indices().exists(request -> request.index(index)).value()) {
					client.indices().delete(request -> request.index(index));
				}
			}
			finally {
				client._transport().close();
			}
		}
	}

}
