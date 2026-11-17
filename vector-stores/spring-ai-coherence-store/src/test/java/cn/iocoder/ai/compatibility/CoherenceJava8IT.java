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
import java.util.stream.Stream;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.Session;
import com.tangosol.net.options.WithConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.coherence.CoherenceVectorStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs real Coherence storage and vector indexes on the native Java8 VM.
 *
 * @author 芋道源码
 */
@Timeout(120)
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "compat.coherence.tests", matches = "true")
class CoherenceJava8IT {

	private static Process peer;

	private static Session javaSession;

	private static Session pofSession;

	@BeforeAll
	@Timeout(45)
	static void start() throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		System.setProperty("coherence.cluster", "yudao-native8-" + UUID.randomUUID());
		System.setProperty("coherence.localhost", "127.0.0.1");
		System.setProperty("coherence.wka", "127.0.0.1");
		System.setProperty("coherence.ttl", "0");
		System.setProperty("coherence.log.level", "2");
		cn.iocoder.ai.compatibility.CoherenceTestPorts.configure();
		javaSession = Session.create(WithConfiguration.using("coherence-ai-java8-java-cache.xml"));
		pofSession = Session.create(WithConfiguration.using("coherence-ai-java8-pof-cache.xml"));
		pofSession.getCache("warmup").size();
		java.util.List<String> command = new java.util.ArrayList<>();
		command.add(System.getProperty("java.home") + "/bin/java");
		command.add("-Djava.net.preferIPv4Stack=true");
		command.add("-Dcoherence.localport=0");
		command.add("-Dcoherence.wka.port=" + CacheFactory.ensureCluster().getLocalMember().getPort());
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		for (String property : Arrays.asList("coherence.cluster", "coherence.localhost", "coherence.wka",
				"coherence.clusterport", "coherence.ttl", "coherence.log.level")) {
			command.add("-D" + property + "=" + System.getProperty(property));
		}
		command.add("-Dcoherence.cacheconfig=coherence-ai-java8-pof-cache.xml");
		command.add("com.tangosol.net.DefaultCacheServer");
		peer = new ProcessBuilder(command).redirectErrorStream(true)
			.redirectOutput(new java.io.File("target/coherence-native8-peer.log"))
			.start();
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
		while (CacheFactory.ensureCluster().getMemberSet().size() < 2 && System.nanoTime() < deadline) {
			assertTrue(peer.isAlive(), "Native8 peer exited; inspect target/coherence-native8-peer.log");
			Thread.sleep(50);
		}
		assertEquals(2, CacheFactory.ensureCluster().getMemberSet().size(),
				"Both native8 processes must be members before querying");
	}

	@AfterAll
	static void stop() throws Exception {
		try {
			if (javaSession != null) {
				javaSession.close();
			}
			if (pofSession != null) {
				pofSession.close();
			}
		}
		finally {
			try {
				CacheFactory.shutdown();
			}
			finally {
				if (peer != null) {
					peer.destroy();
					if (!peer.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
						peer.destroyForcibly();
						assertTrue(peer.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
					}
				}
			}
		}
	}

	static Stream<Arguments> cases() {
		return Arrays.stream(CoherenceVectorStore.IndexType.values())
			.flatMap(index -> Stream.of(Arguments.of(false, index), Arguments.of(true, index)));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void actualCachePreservesScoresFiltersMetadataAndDeletes(boolean pof, CoherenceVectorStore.IndexType index)
			throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		Session session = pof ? pofSession : javaSession;
		EmbeddingModel model = mock(EmbeddingModel.class);
		when(model.dimensions()).thenReturn(2);
		when(model.embed("query")).thenReturn(new float[] { 1, 0 });
		when(model.embed(any(Document.class))).thenAnswer(invocation -> {
			Document doc = invocation.getArgument(0);
			return "same".equals(doc.getId()) ? new float[] { 1, 0 }
					: "near".equals(doc.getId()) ? new float[] { 0.8f, 0.6f } : new float[] { 0, 1 };
		});
		String name = "native8-" + UUID.randomUUID();
		CoherenceVectorStore store = CoherenceVectorStore.builder(session, model)
			.mapName(name)
			.indexType(index)
			.build();
		store.afterPropertiesSet();
		if (pof) {
			assertEquals(2, session.getCache(name).getCacheService().getInfo().getServiceMembers().size(),
					"POF service must span both JVMs");
		}
		assertEquals(0, store.similaritySearch(SearchRequest.builder().query("query").topK(3).build()).size());
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("category", "finance");
		metadata.put("year", 2026);
		store.add(Arrays.asList(Document.builder().id("same").text("预算 café 你好").metadata(metadata).build(),
				Document.builder().id("near").text("near").metadata(metadata).build(),
				Document.builder()
					.id("orthogonal")
					.text("other")
					.metadata(Collections.singletonMap("category", "engineering"))
					.build()));
		List<Document> results = store.similaritySearch(SearchRequest.builder().query("query").topK(3).build());
		assertEquals(3, results.size());
		assertEquals("same", results.get(0).getId());
		assertEquals(1.0, results.get(0).getScore(), 0.00001);
		assertEquals("预算 café 你好", results.get(0).getText());
		assertEquals(2026, results.get(0).getMetadata().get("year"));
		List<Document> filtered = store.similaritySearch(
				SearchRequest.builder().query("query").topK(3).filterExpression("category == 'finance'").build());
		assertEquals(2, filtered.size());
		assertTrue(filtered.stream().allMatch(doc -> "finance".equals(doc.getMetadata().get("category"))));
		assertEquals(1,
				store.similaritySearch(SearchRequest.builder().query("query").topK(3).similarityThreshold(0.9).build())
					.size());
		store.delete(Collections.singletonList("same"));
		assertEquals(2, session.getCache(name).size());
		assertEquals(0,
				store.similaritySearch(SearchRequest.builder().query("query").topK(3).similarityThreshold(0.9).build())
					.size());
		assertEquals(session, store.getNativeClient().get());
		session.getCache(name).destroy();
	}

}
