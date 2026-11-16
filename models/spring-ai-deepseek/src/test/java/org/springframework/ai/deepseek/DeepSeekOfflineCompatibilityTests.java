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

package org.springframework.ai.deepseek;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Complete local HTTP model flows without external provider credentials. The same test
 * source is intended for all compatibility branches.
 *
 * @author 芋道源码
 */
class DeepSeekOfflineCompatibilityTests {

	private MockWebServer server;

	@BeforeEach
	void start() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
	}

	@AfterEach
	void stop() throws Exception {
		this.server.shutdown();
	}

	private DeepSeekChatModel model() {
		return DeepSeekChatModel.builder()
			.deepSeekApi(DeepSeekApi.builder().baseUrl(this.server.url("/").toString()).apiKey("local-test").build())
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.build();
	}

	private MockResponse json(String message, String reason) {
		return new MockResponse().setHeader("Content-Type", "application/json")
			.setBody("{\"id\":\"local\",\"object\":\"chat.completion\",\"created\":123,\"model\":\"deepseek-chat\","
					+ "\"choices\":[{\"index\":0,\"message\":" + message + ",\"finish_reason\":\"" + reason + "\"}],"
					+ "\"usage\":{\"prompt_tokens\":4,\"completion_tokens\":2,\"total_tokens\":6}}");
	}

	@Test
	void retriesActualHttpRequestAndPreservesTextAndUsage() throws Exception {
		this.server.enqueue(new MockResponse().setResponseCode(503).setBody("temporary failure"));
		this.server.enqueue(json("{\"role\":\"assistant\",\"content\":\"hello\"}", "stop"));
		ChatResponse result = model().call(new Prompt("question"));
		assertThat(result.getResult().getOutput().getText()).isEqualTo("hello");
		assertThat(result.getMetadata().getUsage().getTotalTokens()).isEqualTo(6);
		assertThat(this.server.getRequestCount()).isEqualTo(2);
		RecordedRequest request = this.server.takeRequest();
		assertThat(request.getHeader("Authorization")).isEqualTo("Bearer local-test");
		assertThat(request.getBody().readUtf8()).contains("question");
	}

	@Test
	void streamsActualSseChunksThroughChatClient() throws Exception {
		String body = "data: {\"id\":\"s1\",\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"hello \"}}]}\n\n"
				+ "data: {\"id\":\"s1\",\"model\":\"deepseek-chat\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"world\"},\"finish_reason\":\"stop\"}]}\n\n"
				+ "data: [DONE]\n\n";
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body));
		List<String> chunks = ChatClient.builder(model())
			.build()
			.prompt()
			.user("question")
			.stream()
			.content()
			.collectList()
			.block();
		assertThat(String.join("", chunks)).isEqualTo("hello world");
		assertThat(this.server.takeRequest().getBody().readUtf8()).contains("\"stream\":true");
	}

	@Test
	void executesOneToolThenSendsItsResultBackToTheModel() throws Exception {
		this.server.enqueue(json(
				"{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"temperature\",\"arguments\":\"{\\\"city\\\":\\\"Hangzhou\\\"}\"}}]}",
				"tool_calls"));
		this.server.enqueue(json("{\"role\":\"assistant\",\"content\":\"24 degrees\"}", "stop"));
		WeatherTool tool = new WeatherTool();
		String result = ChatClient.builder(model())
			.build()
			.prompt()
			.user("temperature in Hangzhou")
			.tools(tool)
			.call()
			.content();
		assertThat(result).isEqualTo("24 degrees");
		assertThat(tool.calls).hasValue(1);
		assertThat(this.server.getRequestCount()).isEqualTo(2);
		this.server.takeRequest();
		String second = this.server.takeRequest().getBody().readUtf8();
		assertThat(second).contains("\"role\":\"tool\"", "call-1", "24 degrees");
	}

	/**
	 * @author 芋道源码
	 */
	static class WeatherTool {

		private final AtomicInteger calls = new AtomicInteger();

		@Tool(description = "Get the temperature in a city")
		public String temperature(@ToolParam(description = "City name") String city) {
			assertThat(city).isEqualTo("Hangzhou");
			this.calls.incrementAndGet();
			return "24 degrees";
		}

	}

}
