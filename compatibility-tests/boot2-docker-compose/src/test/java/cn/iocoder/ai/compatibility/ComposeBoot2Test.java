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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumes installed Boot2/Java8 Compose jars and proves real vector operations plus
 * VM-exit shutdown.
 *
 * @author 芋道源码
 */
@Timeout(100)
class ComposeBoot2Test {

	@TempDir
	Path temporary;

	@Test
	void childVmStartsServiceConnectsOverHttpAndShutsDownItsOwnProject() throws Exception {
		runCase(false);
	}

	private void runCase(boolean preexisting) throws Exception {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		Path directory = Files.createDirectory(temporary.resolve("compose fixture café"));
		Path compose = directory.resolve("compose.yaml");
		Path proof = directory.resolve("native8-proof.txt");
		String project = "native8compose" + UUID.randomUUID().toString().replace("-", "");
		String yaml = "name: PROJECT\nservices:\n  search:\n    image: opensearchproject/opensearch:2.19.3\n    ports:\n      - target: 9200\n        published: \"0\"\n        host_ip: 127.0.0.1\n        protocol: tcp\n    environment:\n      discovery.type: single-node\n      DISABLE_SECURITY_PLUGIN: \"true\"\n      DISABLE_INSTALL_DEMO_CONFIG: \"true\"\n      OPENSEARCH_JAVA_OPTS: \"-Xms512m -Xmx512m\"\n      OPENSEARCH_INITIAL_ADMIN_PASSWORD: \"Local_test-123!\"\n    healthcheck:\n      test: [\"CMD-SHELL\", \"curl -fsS 'http://localhost:9200/_cluster/health?wait_for_status=yellow' > /dev/null\"]\n      interval: 2s\n      timeout: 5s\n      retries: 30\n    labels:\n      org.yudao.compat.task: boot2-compose-consumer\n"
			.replace("PROJECT", project);
		Files.write(compose, yaml.getBytes(StandardCharsets.UTF_8));
		Path log = directory.resolve("child.log");
		Process child = null;
		try {
			if (preexisting) {
				Process start = new ProcessBuilder("docker", "compose", "--file", compose.toString(), "up", "--detach",
						"--wait")
					.redirectErrorStream(true)
					.redirectOutput(directory.resolve("prestart.log").toFile())
					.start();
				assertTrue(start.waitFor(30, TimeUnit.SECONDS));
				assertEquals(0, start.exitValue());
			}
			child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-Djava.net.preferIPv4Stack=true",
					"-cp", System.getProperty("java.class.path"), ChildMain.class.getName(), compose.toString(),
					proof.toString())
				.redirectErrorStream(true)
				.redirectOutput(log.toFile())
				.start();
			assertTrue(child.waitFor(60, TimeUnit.SECONDS), "Native8 application exit timed out: " + log);
			assertEquals(0, child.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
			assertEquals("1.8:2.7.18:deleted", new String(Files.readAllBytes(proof), StandardCharsets.UTF_8));
			Path ps = directory.resolve("ps.txt");
			Process inspect = new ProcessBuilder("docker", "compose", "--file", compose.toString(), "ps", "--all",
					"--quiet")
				.redirectErrorStream(true)
				.redirectOutput(ps.toFile())
				.start();
			assertTrue(inspect.waitFor(15, TimeUnit.SECONDS));
			assertEquals(0, inspect.exitValue());
			boolean noContainers = new String(Files.readAllBytes(ps), StandardCharsets.UTF_8).trim().isEmpty();
			assertEquals(!preexisting, noContainers,
					"VM exit must remove only projects it started; externally started services must remain running");
		}
		finally {
			if (child != null && child.isAlive()) {
				child.destroy();
				if (!child.waitFor(5, TimeUnit.SECONDS)) {
					child.destroyForcibly();
					child.waitFor(5, TimeUnit.SECONDS);
				}
			}
			Process cleanup = new ProcessBuilder("docker", "compose", "--file", compose.toString(), "down", "--volumes",
					"--remove-orphans")
				.redirectErrorStream(true)
				.redirectOutput(directory.resolve("cleanup.log").toFile())
				.start();
			assertTrue(cleanup.waitFor(20, TimeUnit.SECONDS));
			assertEquals(0, cleanup.exitValue());
		}
	}

	/**
	 * @author 芋道源码
	 */
	public static class ChildMain {

		public static void main(String[] args) throws Exception {
			if (!"1.8".equals(System.getProperty("java.specification.version"))) {
				throw new IllegalStateException("Expected native8");
			}
			for (Class<?> type : new Class<?>[] {
					org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory.class,
					org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories.class,
					org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore.class }) {
				if (!type.getProtectionDomain().getCodeSource().getLocation().getPath().endsWith(".jar")) {
					throw new IllegalStateException("Producer classes leaked into consumer");
				}
				if (java.util.Collections
					.list(type.getClassLoader().getResources(type.getName().replace('.', '/') + ".class"))
					.size() != 1) {
					throw new IllegalStateException("Duplicate provider: " + type);
				}
			}
			ConfigurableApplicationContext context = new SpringApplicationBuilder(App.class)
				.web(WebApplicationType.NONE)
				.properties("spring.main.banner-mode=off", "logging.level.root=WARN",
						"spring.docker.compose.file=" + args[0], "spring.docker.compose.skip.in-tests=false",
						"spring.docker.compose.stop.command=down", "spring.docker.compose.stop.timeout=5s",
						"spring.ai.vectorstore.opensearch.uris=http://127.0.0.1:1",
						"spring.ai.vectorstore.opensearch.aws.enabled=false",
						"spring.ai.vectorstore.opensearch.initialize-schema=true",
						"spring.ai.vectorstore.opensearch.index-name=compat-compose",
						"spring.ai.vectorstore.opensearch.dimensions=2",
						"spring.ai.vectorstore.opensearch.cosine-similarity-score-boost=1.0")
				.run();
			org.springframework.ai.vectorstore.VectorStore store = context
				.getBean(org.springframework.ai.vectorstore.VectorStore.class);
			store.add(java.util.Arrays.asList(
					new org.springframework.ai.document.Document("same", "预算 café 你好",
							java.util.Collections.singletonMap("category", "finance")),
					new org.springframework.ai.document.Document("other", "engineering",
							java.util.Collections.singletonMap("category", "engineering"))));
			org.opensearch.client.opensearch.OpenSearchClient client = context
				.getBean(org.opensearch.client.opensearch.OpenSearchClient.class);
			client.indices().refresh(b -> b.index("compat-compose"));
			org.springframework.ai.vectorstore.SearchRequest query = org.springframework.ai.vectorstore.SearchRequest
				.builder()
				.query("query")
				.topK(2)
				.similarityThreshold(0.9)
				.filterExpression("category == 'finance'")
				.build();
			java.util.List<org.springframework.ai.document.Document> results = store.similaritySearch(query);
			if (results.size() != 1 || Math.abs(results.get(0).getScore() - 1.0) > 0.00001
					|| !"预算 café 你好".equals(results.get(0).getText())) {
				throw new IllegalStateException("Vector contract failed");
			}
			store.delete(java.util.Collections.singletonList("same"));
			client.indices().refresh(b -> b.index("compat-compose"));
			if (!store.similaritySearch(query).isEmpty()) {
				throw new IllegalStateException("Deleted vector remains visible");
			}
			Files.write(java.nio.file.Paths.get(args[1]),
					("1.8:" + org.springframework.boot.SpringBootVersion.getVersion() + ":deleted")
						.getBytes(StandardCharsets.UTF_8));
			System.exit(0);
		}

	}

	/**
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class App {

		@org.springframework.context.annotation.Bean
		org.springframework.ai.embedding.EmbeddingModel model() {
			return new LocalEmbeddings();
		}

	}

	/**
	 * @author 芋道源码 */
	static class LocalEmbeddings implements org.springframework.ai.embedding.EmbeddingModel {

		public int dimensions() {
			return 2;
		}

		public float[] embed(org.springframework.ai.document.Document document) {
			return vector(document.getText());
		}

		public org.springframework.ai.embedding.EmbeddingResponse call(
				org.springframework.ai.embedding.EmbeddingRequest request) {
			java.util.List<org.springframework.ai.embedding.Embedding> values = new java.util.ArrayList<>();
			for (int i = 0; i < request.getInstructions().size(); i++) {
				values.add(new org.springframework.ai.embedding.Embedding(vector(request.getInstructions().get(i)), i));
			}
			return new org.springframework.ai.embedding.EmbeddingResponse(values);
		}

		private float[] vector(String text) {
			return "engineering".equals(text) ? new float[] { 0, 1 } : new float[] { 1, 0 };
		}

	}

}
