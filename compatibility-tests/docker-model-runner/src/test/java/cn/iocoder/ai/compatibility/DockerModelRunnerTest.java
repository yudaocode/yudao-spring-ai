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
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
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
