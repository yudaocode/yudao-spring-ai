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
import java.util.UUID;

import com.oracle.coherence.ai.Float32Vector;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.coherence.CoherenceVectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Consumes installed artifacts through Boot2 without producer classes on the classpath.
 *
 * @author 芋道源码
 */
@Timeout(90)
class CoherenceBoot2Test {

	@Test
	void installedJarsRunAllIndexesWithPofAndUnicode() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		org.slf4j.MDC.put("coherence-compat", "native8");
		try {
			assertEquals("native8", org.slf4j.MDC.get("coherence-compat"));
		}
		finally {
			org.slf4j.MDC.remove("coherence-compat");
		}
		assertEquals("coherence-native8", org.slf4j.MarkerFactory.getMarker("coherence-native8").getName());
		assertEquals(1,
				Collections
					.list(getClass().getClassLoader().getResources("com/oracle/coherence/ai/Float32Vector.class"))
					.size());
		assertTrue(Float32Vector.class.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar"));
		assertTrue(CoherenceVectorStore.class.getProtectionDomain()
			.getCodeSource()
			.getLocation()
			.getPath()
			.endsWith(".jar"));
		System.setProperty("coherence.cluster", "boot2-consumer-" + UUID.randomUUID());
		System.setProperty("coherence.localhost", "127.0.0.1");
		System.setProperty("coherence.wka", "127.0.0.1");
		System.setProperty("coherence.ttl", "0");
		System.setProperty("coherence.log.level", "2");
		System.setProperty("coherence.log", "slf4j");
		System.setProperty("coherence.distributed.localstorage", "true");
		System.setProperty("coherence.serializer", "pof");
		System.setProperty("coherence.pof.config", "coherence-ai-java8-pof-config.xml");
		cn.iocoder.ai.compatibility.CoherenceTestPorts.configure();
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
			.web(WebApplicationType.NONE)
			.properties("spring.main.banner-mode=off", "logging.level.root=WARN")
			.run()) {
			Session session = context.getBean(Session.class);
			EmbeddingModel model = context.getBean(EmbeddingModel.class);
			for (CoherenceVectorStore.IndexType index : CoherenceVectorStore.IndexType.values()) {
				String name = "boot2-" + index;
				CoherenceVectorStore store = CoherenceVectorStore.builder(session, model)
					.mapName(name)
					.indexType(index)
					.build();
				store.afterPropertiesSet();
				store.add(Arrays.asList(
						new Document("same", "预算 café 你好", Collections.singletonMap("category", "finance")),
						new Document("other", "different", Collections.singletonMap("category", "engineering"))));
				List<Document> results = store.similaritySearch(SearchRequest.builder()
					.query("query")
					.topK(2)
					.filterExpression("category == 'finance'")
					.similarityThreshold(0.9)
					.build());
				assertEquals(1, results.size());
				assertEquals("预算 café 你好", results.get(0).getText());
				assertEquals(1.0, results.get(0).getScore(), 0.00001);
				assertEquals(session, store.getNativeClient().get());
				store.delete(Collections.singletonList("same"));
				assertEquals(0,
						store.similaritySearch(SearchRequest.builder().query("query").similarityThreshold(0.9).build())
							.size());
				session.getCache(name).destroy();
			}
		}
		finally {
			CacheFactory.shutdown();
		}
	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

		@Bean
		Session session() {
			return Session.create();
		}

		@Bean
		EmbeddingModel model() {
			EmbeddingModel model = mock(EmbeddingModel.class);
			when(model.dimensions()).thenReturn(2);
			when(model.embed("query")).thenReturn(new float[] { 1, 0 });
			when(model.embed(any(Document.class)))
				.thenAnswer(call -> "same".equals(((Document) call.getArgument(0)).getId()) ? new float[] { 1, 0 }
						: new float[] { 0, 1 });
			return model;
		}

	}

}
