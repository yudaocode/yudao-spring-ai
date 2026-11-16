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

package io.modelcontextprotocol.compat;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the actual source-ported MCP client and HTTP transport on Java 8.
 *
 * @author 芋道源码
 */
@Timeout(20)
class McpJava8HttpTests {

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initializesListsAndCallsToolsOverJsonAndSse(boolean sse) throws Exception {
        assertThat(System.getProperty("java.specification.version")).isEqualTo("1.8");
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger initialized = new AtomicInteger();
        AtomicInteger customized = new AtomicInteger();
        try (MockWebServer server = new MockWebServer()) {
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (!"POST".equals(request.getMethod())) {
                        return new MockResponse().setResponseCode(405);
                    }
                    try {
                        JsonNode message = mapper.readTree(request.getBody().readUtf8());
                        String method = message.path("method").asText();
                        if ("java8-client".equals(request.getHeader("X-Client"))) {
                            customized.incrementAndGet();
                        }
                        String result;
                        if ("initialize".equals(method)) {
                            result = "{\"protocolVersion\":" + mapper.writeValueAsString(message.path("params").path("protocolVersion").asText())
                                    + ",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"local\",\"version\":\"1\"}}";
                        }
                        else if ("notifications/initialized".equals(method)) {
                            initialized.incrementAndGet();
                            return new MockResponse().setResponseCode(202);
                        }
                        else if ("tools/list".equals(method)) {
                            result = "{\"tools\":[{\"name\":\"echo\",\"description\":\"echo\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},\"required\":[\"message\"]}}]}";
                        }
                        else if ("tools/call".equals(method)) {
                            calls.incrementAndGet();
                            result = "{\"content\":[{\"type\":\"text\",\"text\":"
                                    + mapper.writeValueAsString(message.path("params").path("arguments").path("message").asText())
                                    + "}],\"isError\":false}";
                        }
                        else {
                            return new MockResponse().setResponseCode(400);
                        }
                        String json = "{\"jsonrpc\":\"2.0\",\"id\":" + message.get("id") + ",\"result\":" + result + "}";
                        return new MockResponse().setHeader("Content-Type", sse ? "text/event-stream" : "application/json")
                                .setBody(sse ? "event: message\ndata: " + json + "\n\n" : json);
                    }
                    catch (Exception error) {
                        return new MockResponse().setResponseCode(500).setBody(error.toString());
                    }
                }
            });
            server.start();
            HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(server.url("/").toString())
                    .endpoint("/mcp").addHttpRequestCustomizer((builder, method, uri, body, context) -> builder.header("X-Client", "java8-client"))
                    .build();
            try (McpSyncClient client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(5)).build()) {
                assertThat(client.initialize().serverInfo().name()).isEqualTo("local");
                assertThat(client.listTools().tools()).extracting(McpSchema.Tool::name).containsExactly("echo");
                McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest("echo", Collections.singletonMap("message", "hello Java 8")));
                assertThat(result.isError()).isFalse();
                assertThat(((McpSchema.TextContent) result.content().get(0)).text()).isEqualTo("hello Java 8");
                assertThat(calls.get()).isEqualTo(1);
                assertThat(initialized.get()).isEqualTo(1);
                assertThat(customized.get()).isGreaterThanOrEqualTo(4);
            }
        }
    }
}
