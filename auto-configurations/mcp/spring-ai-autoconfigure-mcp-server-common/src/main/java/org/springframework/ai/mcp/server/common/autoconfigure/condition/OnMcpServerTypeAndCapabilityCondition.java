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

package org.springframework.ai.mcp.server.common.autoconfigure.condition;

import java.util.Map;

import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches server type and capability with the upstream case and missing-value rules.
 *
 * @author 芋道源码
 */
final class OnMcpServerTypeAndCapabilityCondition extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Map<String, Object> attributes = metadata
			.getAnnotationAttributes(ConditionalOnMcpServerTypeAndCapability.class.getName());
		String type = (String) attributes.get("type");
		String capability = (String) attributes.get("capability");
		String prefix = McpServerProperties.CONFIG_PREFIX;
		String actualType = context.getEnvironment().getProperty(prefix + ".type", "SYNC");
		String enabled = context.getEnvironment().getProperty(prefix + ".capabilities." + capability, "true");
		boolean matches = type.equalsIgnoreCase(actualType) && "true".equalsIgnoreCase(enabled);
		return matches ? ConditionOutcome.match(type + " " + capability + " capability enabled")
				: ConditionOutcome.noMatch(type + " " + capability + " capability disabled or different server type");
	}

}
