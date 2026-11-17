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
package org.opensearch.client.util;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5Transport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the actual SDK codec, streaming frame parser and HTTP transport on Java 8.
 *
 * @author 芋道源码
 */
@Timeout(30)
class OpenSearchClientJava8Tests {

	@ParameterizedTest
	@ValueSource(strings = { "中文/a b", "café+空格", "%raw", "😀/路径", "simple" })
	void percentCodecRoundTripsUnicodeAndReservedCharacters(String input) {
		assertEquals("1.8", System.getProperty("java.specification.version"));
		String encoded = PercentCodec.RFC3986_PATH.encode(input);
		assertEquals(input, PercentCodec.RFC3986_PATH.decode(encoded));
	}

	@Test
	void streamingFramesResetBufferPositionWithTheJava8Api() throws Exception {
		Method frame = ApacheHttpClient5Transport.class.getDeclaredMethod("frame", ByteBuffer.class);
		frame.setAccessible(true);
		@SuppressWarnings("unchecked")
		Collection<ByteBuffer> buffers = (Collection<ByteBuffer>) frame.invoke(null,
				ByteBuffer.wrap("你好\r\nworld\r\nlast".getBytes(StandardCharsets.UTF_8)));
		List<String> values = new ArrayList<>();
		for (ByteBuffer buffer : buffers) {
			byte[] bytes = new byte[buffer.remaining()];
			buffer.get(bytes);
			values.add(new String(bytes, StandardCharsets.UTF_8));
		}
		assertEquals(java.util.Arrays.asList("你好", "world", "last"), values);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void realSdkSearchUsesJackson2AndTheNative8HttpTransport(boolean async) throws Exception {
		AtomicReference<String> path = new AtomicReference<>();
		AtomicReference<String> request = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			try {
				path.set(exchange.getRequestURI().getRawPath());
				java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
				byte[] buffer = new byte[1024];
				int count;
				while ((count = exchange.getRequestBody().read(buffer)) >= 0) {
					body.write(buffer, 0, count);
				}
				request.set(new String(body.toByteArray(), StandardCharsets.UTF_8));
				byte[] response = ("{\"took\":1,\"timed_out\":false,\"_shards\":{\"total\":1,\"successful\":1,\"failed\":0},"
						+ "\"hits\":{\"total\":{\"value\":1,\"relation\":\"eq\"},\"max_score\":1.0,\"hits\":[{"
						+ "\"_index\":\"中文-index\",\"_id\":\"1\",\"_score\":1.0,\"_source\":{\"text\":\"你好\"}}]}}")
					.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
				exchange.sendResponseHeaders(200, response.length);
				try (java.io.OutputStream output = exchange.getResponseBody()) {
					output.write(response);
				}
			}
			finally {
				exchange.close();
			}
		});
		server.start();
		try (ApacheHttpClient5Transport transport = ApacheHttpClient5TransportBuilder
			.builder(new HttpHost("http", "127.0.0.1", server.getAddress().getPort()))
			.setMapper(new JacksonJsonpMapper())
			.build()) {
			OpenSearchClient client = new OpenSearchClient(transport);
			SearchRequest search = new SearchRequest.Builder().index("中文-index")
				.query(query -> query.matchAll(match -> match))
				.build();
			SearchResponse<Map> response = async ? new OpenSearchAsyncClient(transport).search(search, Map.class)
				.get(5, java.util.concurrent.TimeUnit.SECONDS) : client.search(search, Map.class);
			assertEquals(1, response.hits().hits().size());
			assertEquals("你好", response.hits().hits().get(0).source().get("text"));
			assertNotNull(path.get());
			assertTrue(path.get().contains("/_search"), path.get());
			assertTrue(request.get().contains("match_all"), request.get());
		}
		finally {
			server.stop(0);
		}
	}

}
