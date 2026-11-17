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
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpensearchContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchConnectionDetails;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Consumes installed Java8 jars with real Boot2 SPI and an isolated OpenSearch server.
 *
 * @author 芋道源码
 */
@Timeout(180)
class ServiceConnectionBoot2Test {

	@Test
	void installedServiceConnectionOverridesPropertiesAndStopsContainer() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		for (Class<?> type : new Class<?>[] { ConnectionDetailsFactories.class, ServiceConnection.class,
				OpensearchContainer.class }) {
			assertTrue(type.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"),
					type.getName());
			assertEquals(1,
					Collections
						.list(getClass().getClassLoader().getResources(type.getName().replace('.', '/') + ".class"))
						.size());
		}
		OpensearchContainer<?> container = null;
		try {
			try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "logging.level.root=WARN",
						"spring.ai.vectorstore.opensearch.uris=http://127.0.0.1:1",
						"spring.ai.vectorstore.opensearch.aws.enabled=false",
						"spring.ai.vectorstore.opensearch.initialize-schema=true",
						"spring.ai.vectorstore.opensearch.index-name=compat-service",
						"spring.ai.vectorstore.opensearch.dimensions=2",
						"spring.ai.vectorstore.opensearch.cosine-similarity-score-boost=1.0")
				.run()) {
				container = context.getBean(OpensearchContainer.class);
				assertTrue(container.isRunning());
				OpenSearchConnectionDetails details = context.getBean(OpenSearchConnectionDetails.class);
				assertEquals(Collections.singletonList(container.getHttpHostAddress()), details.getUris());
				VectorStore store = context.getBean(VectorStore.class);
				store.add(Arrays.asList(
						new Document("same", "预算 café 你好", Collections.singletonMap("category", "finance")),
						new Document("other", "engineering", Collections.singletonMap("category", "engineering"))));
				OpenSearchClient client = context.getBean(OpenSearchClient.class);
				client.indices().refresh(b -> b.index("compat-service"));
				SearchRequest query = SearchRequest.builder()
					.query("query")
					.topK(2)
					.similarityThreshold(0.9)
					.filterExpression("category == 'finance'")
					.build();
				List<Document> results = store.similaritySearch(query);
				assertEquals(1, results.size());
				assertEquals("预算 café 你好", results.get(0).getText());
				assertEquals(1.0, results.get(0).getScore(), 0.00001);
				store.delete(Collections.singletonList("same"));
				client.indices().refresh(b -> b.index("compat-service"));
				assertTrue(store.similaritySearch(query).isEmpty());
			}
			assertFalse(container.isRunning(), "Closing Boot must stop the owned container");
		}
		finally {
			if (container != null && container.isRunning()) {
				container.stop();
			}
		}
	}

	@Test
	void native8LoggingAndShadedGuavaFallbacksRemainUsable() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		org.slf4j.MDC.put("compat-native8", "ok");
		try {
			assertEquals("ok", org.slf4j.MDC.get("compat-native8"));
		}
		finally {
			org.slf4j.MDC.remove("compat-native8");
		}
		assertEquals("native8", org.slf4j.MarkerFactory.getMarker("native8").getName());
		assertTrue(org.testcontainers.shaded.com.google.common.primitives.UnsignedBytes.lexicographicalComparator()
			.compare(new byte[] { 1 }, new byte[] { 2 }) < 0);
		assertEquals(128,
				org.testcontainers.shaded.com.google.common.hash.Hashing.murmur3_128()
					.hashBytes(new byte[] { 1, 2, 3 })
					.bits());
		org.testcontainers.shaded.com.google.common.util.concurrent.SettableFuture<String> future = org.testcontainers.shaded.com.google.common.util.concurrent.SettableFuture
			.create();
		assertTrue(future.set("ok"));
		assertEquals("ok", future.get(3, java.util.concurrent.TimeUnit.SECONDS));
		org.testcontainers.shaded.com.google.common.cache.Cache<Object, Object> cache = org.testcontainers.shaded.com.google.common.cache.CacheBuilder
			.newBuilder()
			.recordStats()
			.build();
		cache.put("key", "value");
		assertEquals("value", cache.getIfPresent("key"));
		assertEquals(1L, cache.stats().hitCount());
	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

		@Bean
		@ServiceConnection
		OpensearchContainer<?> opensearch() {
			return new OpensearchContainer<>("opensearchproject/opensearch:2.19.3")
				.withEnv("OPENSEARCH_JAVA_OPTS", "-Xms512m -Xmx512m")
				.withLabel("org.yudao.compat.task", "boot2-testcontainers-consumer");
		}

		@Bean
		EmbeddingModel model() {
			EmbeddingModel model = mock(EmbeddingModel.class);
			when(model.dimensions()).thenReturn(2);
			when(model.embed("query")).thenReturn(new float[] { 1, 0 });
			when(model.embed(org.mockito.ArgumentMatchers.<Document>anyList(), any(), any())).thenAnswer(call -> {
				List<Document> documents = call.getArgument(0);
				return documents.stream()
					.map(document -> "same".equals(document.getId()) ? new float[] { 1, 0 } : new float[] { 0, 1 })
					.collect(java.util.stream.Collectors.toList());
			});
			return model;
		}

	}

}
