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

package org.springframework.ai.zhipuai;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.util.StringUtils;

/**
 * Legacy ZhiPu message retained for persisted Alibaba graph states. Reasoning is stored
 * as ordered message parts, like the Spring AI 2.1 assistant message model. This
 * compatibility type does not provide the removed ZhiPu model integration.
 *
 * @author 芋道源码
 * @since 2.1.0
 * @deprecated use {@link AssistantMessage} and {@link AssistantMessage#getReasoning()}
 */
@Deprecated(since = "2.1.0", forRemoval = true)
public class ZhiPuAiAssistantMessage extends AssistantMessage {

	private @Nullable String reasoningOverride;

	private boolean overridden;

	protected ZhiPuAiAssistantMessage(List<MessagePart> parts, Map<String, Object> properties) {
		super(parts, properties);
	}

	public @Nullable String getReasoningContent() {
		if (this.overridden) {
			return this.reasoningOverride;
		}
		return getReasoning().isEmpty() ? null
				: getReasoning().stream()
					.map(ReasoningPart::text)
					.filter(Objects::nonNull)
					.collect(Collectors.joining());
	}

	public ZhiPuAiAssistantMessage setReasoningContent(@Nullable String reasoningContent) {
		this.reasoningOverride = reasoningContent;
		this.overridden = true;
		return this;
	}

	/**
	 * Whether an explicit setter override takes precedence over the immutable parts.
	 * @return whether the reasoning content was explicitly overridden
	 */
	@com.fasterxml.jackson.annotation.JsonIgnore
	public boolean hasReasoningContentOverride() {
		return this.overridden;
	}

	@Override
	public Builder mutate() {
		Builder builder = builder().parts(getParts()).properties(getMetadata());
		if (this.overridden) {
			builder.reasoningContent(this.reasoningOverride);
		}
		return builder;
	}

	@Override
	public ZhiPuAiAssistantMessage copy() {
		return mutate().build();
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ZhiPuAiAssistantMessage that)) {
			return false;
		}
		return super.equals(other) && Objects.equals(getReasoningContent(), that.getReasoningContent());
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), getReasoningContent());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends AssistantMessage.Builder<Builder> {

		private @Nullable String reasoningContent;

		private boolean reasoningSet;

		public Builder reasoningContent(@Nullable String reasoningContent) {
			this.reasoningContent = reasoningContent;
			this.reasoningSet = true;
			return this;
		}

		@Override
		protected boolean supportsParts() {
			return true;
		}

		@Override
		protected List<MessagePart> buildParts() {
			List<MessagePart> parts = super.buildParts();
			if (this.reasoningSet) {
				List<ReasoningPart> reasoning = StringUtils.hasLength(this.reasoningContent)
						? List.of(ReasoningPart.of(this.reasoningContent)) : List.of();
				replace(parts, ReasoningPart.class, reasoning, 0);
			}
			return parts;
		}

		@Override
		public ZhiPuAiAssistantMessage build() {
			return new ZhiPuAiAssistantMessage(buildParts(), this.properties);
		}

	}

}
