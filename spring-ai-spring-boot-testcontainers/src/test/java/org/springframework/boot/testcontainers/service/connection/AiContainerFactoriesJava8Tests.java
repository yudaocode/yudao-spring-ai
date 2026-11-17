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

package org.springframework.boot.testcontainers.service.connection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.DockerMcpGatewayContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.milvus.MilvusContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.qdrant.QdrantContainer;
import org.testcontainers.typesense.TypesenseContainer;
import org.testcontainers.weaviate.WeaviateContainer;

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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.service.connection.ConnectionDetailsFactories;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises each real AI factory through Spring SPI on native8 with controlled
 * containers. Real Docker lifecycle/protocol acceptance is tested separately.
 *
 * @author 芋道源码
 */
class AiContainerFactoriesJava8Tests {

	@ServiceConnection
	static Container<?> annotationSource;

	@BeforeEach
	void nativeVm() {
		assertEquals("1.8", System.getProperty("java.specification.version"));
	}

	@Test
	void chromaWithoutAuthPreservesHostPortAndNullToken() throws Exception {
		ChromaConnectionDetails details = chroma(Collections.emptyMap());
		assertEquals("http://127.0.0.1", details.getHost());
		assertEquals(18000, details.getPort());
		assertNull(details.getKeyToken());
	}

	@Test
	void chromaSupportsLegacyAuthVariable() throws Exception {
		assertEquals("old-token",
				chroma(Collections.singletonMap("CHROMA_SERVER_AUTH_CREDENTIALS", "old-token")).getKeyToken());
	}

	@Test
	void chromaSupportsNewAuthVariable() throws Exception {
		assertEquals("new-token",
				chroma(Collections.singletonMap("CHROMA_SERVER_AUTHN_CREDENTIALS", "new-token")).getKeyToken());
	}

	@Test
	void chromaRetainsLegacyPrecedenceWhenBothVariablesArePresent() throws Exception {
		Map<String, String> env = new HashMap<>();
		env.put("CHROMA_SERVER_AUTH_CREDENTIALS", "old-token");
		env.put("CHROMA_SERVER_AUTHN_CREDENTIALS", "new-token");
		assertEquals("old-token", chroma(env).getKeyToken());
	}

	private ChromaConnectionDetails chroma(Map<String, String> env) throws Exception {
		ChromaDBContainer container = mock(ChromaDBContainer.class);
		when(container.getHost()).thenReturn("127.0.0.1");
		when(container.getMappedPort(8000)).thenReturn(18000);
		when(container.getEnvMap()).thenReturn(env);
		return details(container, ChromaDBContainer.class, "chromadb/chroma:1.0", ChromaConnectionDetails.class);
	}

	@Test
	void dockerMcpMapsGatewayEndpointAndSsePath() throws Exception {
		DockerMcpGatewayContainer container = mock(DockerMcpGatewayContainer.class);
		when(container.getEndpoint()).thenReturn("http://127.0.0.1:18080");
		McpSseClientConnectionDetails details = details(container, DockerMcpGatewayContainer.class,
				"docker/mcp-gateway:latest", McpSseClientConnectionDetails.class);
		assertEquals(1, details.getConnections().size());
		assertEquals("http://127.0.0.1:18080", details.getConnections().get("gateway").url());
		assertEquals("/sse", details.getConnections().get("gateway").sseEndpoint());
	}

	@Test
	void milvusPreservesMappedGrpcAddress() throws Exception {
		MilvusContainer container = mock(MilvusContainer.class);
		when(container.getHost()).thenReturn("127.0.0.1");
		when(container.getMappedPort(19530)).thenReturn(29530);
		MilvusServiceClientConnectionDetails details = details(container, MilvusContainer.class, "milvusdb/milvus:v2.4",
				MilvusServiceClientConnectionDetails.class);
		assertEquals("127.0.0.1", details.getHost());
		assertEquals(29530, details.getPort());
	}

	@Test
	void ollamaPreservesEndpoint() throws Exception {
		OllamaContainer container = mock(OllamaContainer.class);
		when(container.getEndpoint()).thenReturn("http://127.0.0.1:21434");
		assertEquals("http://127.0.0.1:21434",
				details(container, OllamaContainer.class, "ollama/ollama:latest", OllamaConnectionDetails.class)
					.getBaseUrl());
	}

	@Test
	void openSearchPreservesUriAndNullableCredentials() throws Exception {
		OpensearchContainer<?> container = mock(OpensearchContainer.class);
		when(container.getHttpHostAddress()).thenReturn("http://127.0.0.1:19200");
		OpenSearchConnectionDetails details = details(container, OpensearchContainer.class,
				"opensearchproject/opensearch:2.19.3", OpenSearchConnectionDetails.class);
		assertEquals(Collections.singletonList("http://127.0.0.1:19200"), details.getUris());
		assertNull(details.getUsername());
		assertNull(details.getPassword());
	}

	@Test
	void openSearchRetainsEnabledSecurityCredentials() throws Exception {
		OpensearchContainer<?> container = mock(OpensearchContainer.class);
		when(container.isSecurityEnabled()).thenReturn(true);
		when(container.getUsername()).thenReturn("admin");
		when(container.getPassword()).thenReturn("local-test-password");
		OpenSearchConnectionDetails details = details(container, OpensearchContainer.class,
				"opensearchproject/opensearch:2.19.3", OpenSearchConnectionDetails.class);
		assertEquals("admin", details.getUsername());
		assertEquals("local-test-password", details.getPassword());
	}

	@Test
	void awsLocalstackRetainsDomainRegionAndCredentials() throws Exception {
		LocalStackContainer container = mock(LocalStackContainer.class);
		when(container.getRegion()).thenReturn("us-east-1");
		when(container.getAccessKey()).thenReturn("test-access");
		when(container.getSecretKey()).thenReturn("test-secret");
		when(container.getMappedPort(4566)).thenReturn(14566);
		AwsOpenSearchConnectionDetails details = details(container, LocalStackContainer.class,
				"localstack/localstack:latest", AwsOpenSearchConnectionDetails.class);
		assertEquals("us-east-1", details.getRegion());
		assertEquals("test-access", details.getAccessKey());
		assertEquals("test-secret", details.getSecretKey());
		assertEquals("domain.us-east-1.opensearch.localhost.localstack.cloud:14566", details.getHost("domain"));
		assertThrows(IllegalArgumentException.class, () -> details.getHost(null));
	}

	@Test
	void qdrantPreservesGrpcPortAndOptionalApiKey() throws Exception {
		QdrantContainer container = mock(QdrantContainer.class);
		when(container.getHost()).thenReturn("127.0.0.1");
		when(container.getMappedPort(6334)).thenReturn(16334);
		Map<String, String> env = new HashMap<>();
		env.put("QDRANT__SERVICE__API_KEY", "local-token");
		when(container.getEnvMap()).thenReturn(env);
		QdrantConnectionDetails details = details(container, QdrantContainer.class, "qdrant/qdrant:latest",
				QdrantConnectionDetails.class);
		assertEquals("127.0.0.1", details.getHost());
		assertEquals(16334, details.getPort());
		assertEquals("local-token", details.getApiKey());
		env.clear();
		assertNull(details.getApiKey());
	}

	@Test
	void typesenseRetainsProtocolParsedPortAndApiKey() throws Exception {
		TypesenseContainer container = mock(TypesenseContainer.class);
		when(container.getHost()).thenReturn("127.0.0.1");
		when(container.getHttpPort()).thenReturn("18108");
		when(container.getApiKey()).thenReturn("local-token");
		TypesenseConnectionDetails details = details(container, TypesenseContainer.class, "typesense/typesense:latest",
				TypesenseConnectionDetails.class);
		assertEquals("127.0.0.1", details.getHost());
		assertEquals("http", details.getProtocol());
		assertEquals(18108, details.getPort());
		assertEquals("local-token", details.getApiKey());
	}

	@Test
	void weaviateRetainsItsHttpAddress() throws Exception {
		WeaviateContainer container = mock(WeaviateContainer.class);
		when(container.getHttpHostAddress()).thenReturn("127.0.0.1:18080");
		assertEquals("127.0.0.1:18080", details(container, WeaviateContainer.class, "semitechnologies/weaviate:latest",
				WeaviateConnectionDetails.class)
			.getHost());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <D extends ConnectionDetails> D details(Container<?> container, Class<?> type, String image,
			Class<D> resultType) throws Exception {
		MergedAnnotation<ServiceConnection> annotation = MergedAnnotations
			.from(AiContainerFactoriesJava8Tests.class.getDeclaredField("annotationSource"))
			.get(ServiceConnection.class);
		ContainerConnectionSource source = new ContainerConnectionSource("fixture", null, type, image, annotation,
				() -> container);
		Map<Class<?>, ConnectionDetails> found = new ConnectionDetailsFactories().getConnectionDetails(source, true);
		D details = resultType.cast(found.get(resultType));
		((InitializingBean) details).afterPropertiesSet();
		return details;
	}

}
