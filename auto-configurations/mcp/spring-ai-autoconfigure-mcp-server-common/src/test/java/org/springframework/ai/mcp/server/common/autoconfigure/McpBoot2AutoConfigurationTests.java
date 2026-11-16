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

package org.springframework.ai.mcp.server.common.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.server.common.autoconfigure.condition.ConditionalOnMcpServerTypeAndCapability;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies mapper isolation, explicit qualifiers and conjunctive conditions on Boot2.
 *
 * @author 芋道源码
 */
class McpBoot2AutoConfigurationTests {

	@Test
	void keepsTheApplicationMapperAndAllowsExplicitMcpQualification() {
		new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class, JacksonAutoConfiguration.class))
			.withUserConfiguration(InjectionProbe.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				ObjectMapper mcp = context.getBean("mcpServerJsonMapper", ObjectMapper.class);
				assertThat(context.getBean(NormalInjection.class).mapper).isNotSameAs(mcp);
				assertThat(context.getBean(QualifiedInjection.class).mapper).isSameAs(mcp);
				assertThat(context.getBean(ObjectMapper.class)).isSameAs(context.getBean(NormalInjection.class).mapper);
			});
	}

	@Test
	void leavesAnUnqualifiedOptionalInjectionEmptyWithoutAnApplicationMapper() {
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class))
			.withUserConfiguration(OptionalInjectionProbe.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(NormalInjection.class).mapper).isNull();
				assertThat(context.getBean(QualifiedInjection.class).mapper)
					.isSameAs(context.getBean("mcpServerJsonMapper"));
			});
	}

	@Test
	void preservesAUserApplicationMapperWithoutRequiringPrimary() {
		new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class, JacksonAutoConfiguration.class))
			.withUserConfiguration(UserMapper.class, InjectionProbe.class)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(NormalInjection.class).mapper).isSameAs(context.getBean("userMapper"));
				assertThat(context.getBean(QualifiedInjection.class).mapper)
					.isSameAs(context.getBean("mcpServerJsonMapper"));
			});
	}

	@Test
	void disablesTheMcpMapperAndResolverWithTheServer() {
		new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(McpServerJsonMapperAutoConfiguration.class, JacksonAutoConfiguration.class))
			.withPropertyValues("spring.ai.mcp.server.enabled=false")
			.run(context -> {
				assertThat(context).doesNotHaveBean("mcpServerJsonMapper");
				assertThat(context).doesNotHaveBean("mcpJsonMapperCandidateResolver");
				assertThat(context).hasSingleBean(ObjectMapper.class);
			});
	}

	@Test
	void matchesBothTypeAndCapabilityWithTheUpstreamMissingAndCaseRules() {
		ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(ConditionalProbe.class);
		runner.run(context -> assertThat(context).hasBean("syncTool").doesNotHaveBean("asyncTool"));
		runner.withPropertyValues("spring.ai.mcp.server.type=aSyNc")
			.run(context -> assertThat(context).hasBean("asyncTool").doesNotHaveBean("syncTool"));
		runner.withPropertyValues("spring.ai.mcp.server.capabilities.tool=FaLsE")
			.run(context -> assertThat(context).doesNotHaveBean("syncTool").doesNotHaveBean("asyncTool"));
		runner.withPropertyValues("spring.ai.mcp.server.type=ASYNC", "spring.ai.mcp.server.capabilities.tool=yes")
			.run(context -> assertThat(context).doesNotHaveBean("syncTool").doesNotHaveBean("asyncTool"));
	}

	/**
	 * @author 芋道源码
	 */
	@Configuration(proxyBeanMethods = false)
	static class InjectionProbe {

		@Bean
		NormalInjection normalInjection(ObjectMapper mapper) {
			return new NormalInjection(mapper);
		}

		@Bean
		QualifiedInjection qualifiedInjection(@Qualifier("mcpServerJsonMapper") JsonMapper mapper) {
			return new QualifiedInjection(mapper);
		}

	}

	/**
	 * @author 芋道源码
	 */
	@Configuration(proxyBeanMethods = false)
	static class OptionalInjectionProbe {

		@Bean
		NormalInjection normalInjection(ObjectProvider<ObjectMapper> mapper) {
			return new NormalInjection(mapper.getIfAvailable());
		}

		@Bean
		QualifiedInjection qualifiedInjection(@Qualifier("mcpServerJsonMapper") JsonMapper mapper) {
			return new QualifiedInjection(mapper);
		}

	}

	/**
	 * @author 芋道源码
	 */
	@Configuration(proxyBeanMethods = false)
	static class UserMapper {

		@Bean
		ObjectMapper userMapper() {
			return new ObjectMapper();
		}

	}

	/**
	 * @author 芋道源码
	 */
	@Configuration(proxyBeanMethods = false)
	static class ConditionalProbe {

		@Bean
		@ConditionalOnMcpServerTypeAndCapability(type = "SYNC", capability = "tool")
		String syncTool() {
			return "sync";
		}

		@Bean
		@ConditionalOnMcpServerTypeAndCapability(type = "ASYNC", capability = "tool")
		String asyncTool() {
			return "async";
		}

	}

	/**
	 * @author 芋道源码
	 */
	static class NormalInjection {

		final ObjectMapper mapper;

		NormalInjection(ObjectMapper mapper) {
			this.mapper = mapper;
		}

	}

	/**
	 * @author 芋道源码
	 */
	static class QualifiedInjection {

		final ObjectMapper mapper;

		QualifiedInjection(ObjectMapper mapper) {
			this.mapper = mapper;
		}

	}

}
