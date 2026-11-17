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

package org.springframework.boot.docker.compose.service.connection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.ai.mcp.client.common.autoconfigure.McpSseClientConnectionDetails;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.util.ConnectionDetails;
import org.springframework.ai.vectorstore.chroma.autoconfigure.ChromaConnectionDetails;
import org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusServiceClientConnectionDetails;
import org.springframework.ai.vectorstore.opensearch.autoconfigure.AwsOpenSearchConnectionDetails;
import org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchConnectionDetails;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantConnectionDetails;
import org.springframework.ai.vectorstore.typesense.autoconfigure.TypesenseConnectionDetails;
import org.springframework.ai.vectorstore.weaviate.autoconfigure.WeaviateConnectionDetails;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories;
import org.springframework.boot.docker.compose.core.ConnectionPorts;
import org.springframework.boot.docker.compose.core.ImageReference;
import org.springframework.boot.docker.compose.core.RunningService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Resolves all nine actual Compose factories through SPI and verifies mapped properties.
 * Real Compose process and server acceptance are separate gates.
 *
 * @author 芋道源码
 */
class AiComposeFactoriesJava8Tests {

	static Stream<Arguments> cases() {
		return Stream.of(row("chroma-legacy", "chromadb/chroma:1.0", env("CHROMA_SERVER_AUTH_CREDENTIALS", "legacy"),
				ChromaConnectionDetails.class, details -> {
					ChromaConnectionDetails value = (ChromaConnectionDetails) details;
					assertEquals("http://127.0.0.1", value.getHost());
					assertEquals(18000, value.getPort());
					assertEquals("legacy", value.getKeyToken());
				}),
				row("chroma-ghcr-alias", "ghcr.io/chroma-core/chroma:1.0",
						env("CHROMA_SERVER_AUTHN_CREDENTIALS", "new"), ChromaConnectionDetails.class,
						details -> assertEquals("new", ((ChromaConnectionDetails) details).getKeyToken())),
				row("chroma-old-key-precedence", "chromadb/chroma:1.0",
						env("CHROMA_SERVER_AUTH_CREDENTIALS", "legacy", "CHROMA_SERVER_AUTHN_CREDENTIALS", "new"),
						ChromaConnectionDetails.class,
						details -> assertEquals("legacy", ((ChromaConnectionDetails) details).getKeyToken())),
				row("weaviate-dockerhub", "semitechnologies/weaviate:1.0", env(), WeaviateConnectionDetails.class,
						details -> assertEquals("127.0.0.1:18080", ((WeaviateConnectionDetails) details).getHost())),
				row("weaviate-registry-alias", "cr.weaviate.io/semitechnologies/weaviate:1.0", env(),
						WeaviateConnectionDetails.class,
						details -> assertEquals("127.0.0.1:18080", ((WeaviateConnectionDetails) details).getHost())),
				row("docker-mcp", "docker/mcp-gateway:latest", env(), McpSseClientConnectionDetails.class, details -> {
					McpSseClientConnectionDetails value = (McpSseClientConnectionDetails) details;
					assertEquals("http://127.0.0.1:18811", value.getConnections().get("gateway").url());
					assertEquals("/sse", value.getConnections().get("gateway").sseEndpoint());
				}),
				row("milvus", "milvusdb/milvus:latest", env(), MilvusServiceClientConnectionDetails.class, details -> {
					MilvusServiceClientConnectionDetails value = (MilvusServiceClientConnectionDetails) details;
					assertEquals("127.0.0.1", value.getHost());
					assertEquals(29530, value.getPort());
				}),
				row("ollama", "ollama/ollama:latest", env(), OllamaConnectionDetails.class,
						details -> assertEquals("http://127.0.0.1:21434",
								((OllamaConnectionDetails) details).getBaseUrl())),
				row("opensearch", "opensearchproject/opensearch:2.19.3",
						env("OPENSEARCH_INITIAL_ADMIN_PASSWORD", "local-password"), OpenSearchConnectionDetails.class,
						details -> {
							OpenSearchConnectionDetails value = (OpenSearchConnectionDetails) details;
							assertEquals(Collections.singletonList("http://127.0.0.1:19200"), value.getUris());
							assertEquals("admin", value.getUsername());
							assertEquals("local-password", value.getPassword());
						}),
				row("aws-localstack", "localstack/localstack:latest", env("DEFAULT_REGION", "us-west-2",
						"AWS_ACCESS_KEY_ID", "local-access", "AWS_SECRET_ACCESS_KEY", "local-secret"),
						AwsOpenSearchConnectionDetails.class, details -> {
							AwsOpenSearchConnectionDetails value = (AwsOpenSearchConnectionDetails) details;
							assertEquals("us-west-2", value.getRegion());
							assertEquals("local-access", value.getAccessKey());
							assertEquals("local-secret", value.getSecretKey());
							assertEquals("domain.us-west-2.opensearch.localhost.localstack.cloud:14566",
									value.getHost("domain"));
						}),
				row("qdrant", "qdrant/qdrant:latest", env("QDRANT__SERVICE__API_KEY", "local-key"),
						QdrantConnectionDetails.class, details -> {
							QdrantConnectionDetails value = (QdrantConnectionDetails) details;
							assertEquals("127.0.0.1", value.getHost());
							assertEquals(16334, value.getPort());
							assertEquals("local-key", value.getApiKey());
						}),
				row("typesense", "typesense/typesense:latest", env("TYPESENSE_API_KEY", "local-key"),
						TypesenseConnectionDetails.class, details -> {
							TypesenseConnectionDetails value = (TypesenseConnectionDetails) details;
							assertEquals("127.0.0.1", value.getHost());
							assertEquals("http", value.getProtocol());
							assertEquals(18108, value.getPort());
							assertEquals("local-key", value.getApiKey());
						}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void discoversCurrentImageNamesAndPreservesConnectionProperties(String name, String image, Map<String, String> env,
			Class<? extends ConnectionDetails> type, Consumer<ConnectionDetails> verify) {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		Map<Class<?>, ConnectionDetails> found = new ConnectionDetailsFactories()
			.getConnectionDetails(new DockerComposeConnectionSource(service(image, env, Collections.emptyMap())), true);
		assertEquals(1, found.size());
		verify.accept(type.cast(found.get(type)));
	}

	@Test
	void labelOverridesImageMatchingAndRetainsProperties() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		RunningService service = service("private.invalid/custom:latest", env("QDRANT__SERVICE__API_KEY", "key"),
				Collections.singletonMap("org.springframework.boot.service-connection", "qdrant/qdrant"));
		Map<Class<?>, ConnectionDetails> found = new ConnectionDetailsFactories()
			.getConnectionDetails(new DockerComposeConnectionSource(service), true);
		assertEquals("key", ((QdrantConnectionDetails) found.get(QdrantConnectionDetails.class)).getApiKey());
	}

	@Test
	void unknownImagesDoNotProduceConnectionDetails() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		assertTrue(new ConnectionDetailsFactories()
			.getConnectionDetails(new DockerComposeConnectionSource(
					service("private.invalid/custom:latest", env(), Collections.emptyMap())), false)
			.isEmpty());
	}

	@Test
	void anExplicitNonMatchingLabelSuppressesAnOtherwiseMatchingImage() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		assertTrue(new ConnectionDetailsFactories()
			.getConnectionDetails(
					new DockerComposeConnectionSource(service("qdrant/qdrant:latest", env(),
							Collections.singletonMap("org.springframework.boot.service-connection", "unrelated"))),
					false)
			.isEmpty());
	}

	private static Arguments row(String name, String image, Map<String, String> env,
			Class<? extends ConnectionDetails> type, Consumer<ConnectionDetails> verify) {
		return Arguments.of(name, image, env, type, verify);
	}

	private static Map<String, String> env(String... values) {
		Map<String, String> result = new HashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			result.put(values[i], values[i + 1]);
		}
		return result;
	}

	private static RunningService service(String image, Map<String, String> env, Map<String, String> labels) {
		RunningService service = mock(RunningService.class);
		ConnectionPorts ports = mock(ConnectionPorts.class);
		when(ports.get(anyInt())).thenAnswer(call -> 10000 + ((Integer) call.getArgument(0)));
		when(service.name()).thenReturn("fixture");
		when(service.host()).thenReturn("127.0.0.1");
		when(service.image()).thenReturn(ImageReference.of(image));
		when(service.env()).thenReturn(env);
		when(service.labels()).thenReturn(labels);
		when(service.ports()).thenReturn(ports);
		return service;
	}

}
