/*
 * Copyright 2026-present the original author or authors.
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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the OpenAI starter against a real Docker Model Runner daemon.
 *
 * @author 芋道源码
 */
class DockerModelRunnerTest {

	private static final String QUESTION = "Give one short reason why the sky looks blue.";

	private static ConfigurableApplicationContext context;

	private static OpenAiChatModel chatModel;

	@BeforeAll
	static void startApplication() {
		String baseUrl = Objects.requireNonNull(System.getProperty("dmr.base-url"), "Set -Ddmr.base-url");
		String model = Objects.requireNonNull(System.getProperty("dmr.model"), "Set -Ddmr.model");
		assertThat(SpringBootVersion.getVersion()).isEqualTo(System.getProperty("compat.boot.version"));
		assertThat(System.getProperty("java.specification.version"))
			.isEqualTo(System.getProperty("compat.java.version"));

		context = new SpringApplicationBuilder(Application.class).web(WebApplicationType.NONE)
			.run("--spring.ai.openai.base-url=" + baseUrl, "--spring.ai.openai.api-key=local-fixture",
					"--spring.ai.openai.chat.options.model=" + model, "--spring.ai.openai.chat.options.max-tokens=32",
					"--spring.ai.openai.chat.options.temperature=0", "--spring.ai.openai.timeout=90s",
					"--spring.ai.openai.max-retries=0");
		chatModel = context.getBean(OpenAiChatModel.class);
	}

	@AfterAll
	static void closeApplication() {
		if (context != null) {
			context.close();
		}
	}

	@Test
	void completesChatWithTokenUsage() {
		ChatResponse response = chatModel.call(new Prompt(QUESTION));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput().getText()).isNotBlank();
		assertThat(response.getResult().getMetadata().getFinishReason()).isNotBlank();
		assertThat(response.getMetadata().getModel()).isNotBlank();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isPositive();
	}

	@Test
	void streamsChatWithFinishReasonAndTokenUsage() {
		Prompt prompt = new Prompt(QUESTION);
		List<ChatResponse> chunks = chatModel.stream(prompt).collectList().block(Duration.ofSeconds(90));

		assertThat(chunks).isNotNull().hasSizeGreaterThan(1);
		assertThat(chunks).anySatisfy(chunk -> {
			assertThat(chunk.getResults()).anySatisfy(generation -> {
				assertThat(generation.getOutput().getText()).isNotBlank();
			});
		});
		assertThat(chunks).anySatisfy(chunk -> {
			assertThat(chunk.getResults()).anySatisfy(generation -> {
				assertThat(generation.getMetadata().getFinishReason()).isNotBlank();
			});
		});
		assertThat(chunks).last().satisfies(chunk -> {
			assertThat(chunk.getMetadata().getUsage().getPromptTokens()).isPositive();
			assertThat(chunk.getMetadata().getUsage().getCompletionTokens()).isPositive();
		});
	}

	@Test
	void executesJavaToolAndUsesItsResult() {
		AtomicInteger calls = new AtomicInteger();
		ToolCallback tool = FunctionToolCallback.builder("get_code", () -> {
			calls.incrementAndGet();
			return "RAIN42";
		}).description("Returns the current code.").build();
		OpenAiChatOptions options = qwenOptions().toolCallbacks(Collections.singletonList(tool)).build();
		Prompt prompt = new Prompt(
				Arrays.asList(new SystemMessage("When asked for a code you must call get_code before answering."),
						new UserMessage("Call get_code to find the code, then report that code.")),
				options);

		ChatResponse toolResponse = chatModel.call(prompt);
		assertThat(toolResponse.getResult().getOutput().getToolCalls()).singleElement()
			.satisfies(call -> assertThat(call.name()).isEqualTo("get_code"));
		assertThat(calls).hasValue(0);

		ToolCallingManager manager = context.getBean(ToolCallingManager.class);
		ToolExecutionResult execution = manager.executeToolCalls(prompt, toolResponse);
		ChatResponse finalResponse = chatModel.call(new Prompt(execution.conversationHistory(), options));

		assertThat(calls).hasValue(1);
		assertThat(finalResponse.hasToolCalls()).isFalse();
		assertThat(finalResponse.getResult().getOutput().getText()).contains("RAIN42");
	}

	@Test
	void returnsJsonMatchingStrictSchema() {
		String schema = "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"},"
				+ "\"count\":{\"type\":\"integer\"}},\"required\":[\"city\",\"count\"],\"additionalProperties\":false}";
		OpenAiChatOptions options = qwenOptions()
			.responseFormat(ResponseFormat.builder().jsonSchema(schema).strict(true).build())
			.build();

		ChatResponse response = chatModel.call(new Prompt("Report the city Paris and a count of 2.", options));
		Map<?, ?> result = JsonParser.fromJson(response.getResult().getOutput().getText(), Map.class);

		assertThat(result).hasSize(2);
		assertThat(result.get("city")).isEqualTo("Paris");
		assertThat(result.get("count")).isEqualTo(2);
	}

	private static OpenAiChatOptions.Builder qwenOptions() {
		return OpenAiChatOptions.builder()
			.model(System.getProperty("dmr.tool-model", "ai/qwen3-compat:0.6b"))
			.temperature(0.0)
			.maxTokens(128)
			.extraBody(Collections.singletonMap("chat_template_kwargs",
					Collections.singletonMap("enable_thinking", false)));
	}

	/**
	 * Minimal non-web application that loads the regular OpenAI starter.
	 *
	 * @author 芋道源码
	 */
	@SpringBootConfiguration
	@EnableAutoConfiguration
	static class Application {

	}

}
