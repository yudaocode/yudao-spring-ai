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

package org.springframework.ai.vectorstore.gemfire;

import java.time.Duration;
import java.util.Collections;

import com.vmware.gemfire.testcontainers.AbstractGemFireContainer;
import com.vmware.gemfire.testcontainers.GemFireCluster;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercises the REST vector adapter against the local GemFire VectorDB extension.
 *
 * @author 芋道源码
 */
@Testcontainers(disabledWithoutDocker = true)
class GemFireDockerSmokeIT {

	private static final int HTTP_PORT = 9090;

	@Test
	void writesSearchesAndDeletesInVectorDb() throws Exception {
		try (GemFireCluster cluster = new GemFireCluster(GemFireImage.DEFAULT_IMAGE, 1, 1)) {
			cluster.withGemFireProperty(GemFireCluster.SERVER_GLOB, "http-service-port", Integer.toString(HTTP_PORT))
				.withConfiguration(GemFireCluster.ALL_GLOB,
						container -> container.withTmpFs(Collections.singletonMap("/data", "rw,size=512m")))
				.withConfiguration(GemFireCluster.SERVER_GLOB, container -> container.addExposedPort(HTTP_PORT))
				.withStartupTimeout(GemFireCluster.ALL_GLOB, 120)
				.acceptLicense()
				.start();

			AbstractGemFireContainer<?> server = cluster.getContainers()
				.values()
				.stream()
				.filter(container -> container.getMemberName().startsWith("server-"))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No GemFire server container"));
			GemFireVectorStore store = GemFireVectorStore.builder(new FixedDimensionEmbeddingModel(3))
				.host(server.getHost())
				.port(server.getMappedPort(HTTP_PORT))
				.indexName("compat-docker-vector")
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();

			Document document = new Document("GemFire 本地验证", Collections.singletonMap("source", "docker"));
			store.add(Collections.singletonList(document));
			SearchRequest query = SearchRequest.builder().query("GemFire").topK(1).similarityThresholdAll().build();
			await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
				assertThat(store.similaritySearch(query)).singleElement().satisfies(result -> {
					assertThat(result.getId()).isEqualTo(document.getId());
					assertThat(result.getText()).isEqualTo(document.getText());
				});
			});

			store.delete(Collections.singletonList(document.getId()));
			await().atMost(Duration.ofSeconds(30))
				.untilAsserted(() -> assertThat(store.similaritySearch(query)).isEmpty());
		}
	}

}
