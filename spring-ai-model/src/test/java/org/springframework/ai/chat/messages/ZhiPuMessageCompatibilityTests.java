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

package org.springframework.ai.chat.messages;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.zhipuai.ZhiPuAiAssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies migration of legacy reasoning fields into ordered assistant message parts.
 *
 * @author 芋道源码
 */
@SuppressWarnings("removal")
class ZhiPuMessageCompatibilityTests {

	@Test
	void legacyBuilderRetainsReasoningAndToolCallsAsParts() {
		var tool = new AssistantMessage.ToolCall("tool-1", "function", "lookup", "{}");
		var message = new ZhiPuAiAssistantMessage.Builder().content("answer")
			.reasoningContent("think")
			.toolCalls(List.of(tool))
			.build();
		assertThat(message.getReasoningContent()).isEqualTo("think");
		assertThat(message.getReasoning()).containsExactly(ReasoningPart.of("think"));
		assertThat(message.getText()).isEqualTo("answer");
		assertThat(message.getToolCalls()).containsExactly(tool);
	}

	@Test
	void mutateAndCopyKeepExplicitPartOrdering() {
		var message = ZhiPuAiAssistantMessage.builder()
			.parts(List.of(TextPart.of("first"), ReasoningPart.of("think"), TextPart.of("last")))
			.build();
		assertThat(message.copy().getParts()).containsExactlyElementsOf(message.getParts());
		assertThat(message.mutate().build().getParts()).containsExactlyElementsOf(message.getParts());
	}

	@Test
	void legacySetterIsAppliedWhenCopiedOrMutated() {
		var message = ZhiPuAiAssistantMessage.builder().content("answer").reasoningContent("old").build();
		message.setReasoningContent("new");
		assertThat(message.copy().getReasoning()).containsExactly(ReasoningPart.of("new"));
		assertThat(message.mutate().build().getReasoningContent()).isEqualTo("new");
		assertThat(message.getReasoning()).containsExactly(ReasoningPart.of("old"));
	}

}
