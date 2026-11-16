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

package org.springframework.ai.compat.autoconfigure;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Provides isolated RestClient builders on Boot 2, honoring its RestTemplate settings.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
@AutoConfiguration(after = RestTemplateAutoConfiguration.class)
@ConditionalOnClass(RestClient.class)
public class RestClientAutoConfiguration {

	@Bean
	@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	@ConditionalOnMissingBean(RestClient.Builder.class)
	RestClient.Builder restClientBuilder(ObjectProvider<RestTemplateBuilder> templates,
			ObjectProvider<ObservationRegistry> observations) {
		RestTemplate template = templates.getIfAvailable(RestTemplateBuilder::new).build();
		ObservationRegistry registry = observations.getIfUnique();
		if (registry != null) {
			// RestClient observes HTTP itself. Avoid also counting Boot 2's legacy
			// RestTemplate metrics interceptor, while retaining user interceptors.
			template.getInterceptors()
				.removeIf(interceptor -> interceptor.getClass()
					.getName()
					.equals("org.springframework.boot.actuate.metrics.web.client.MetricsClientHttpRequestInterceptor"));
		}
		RestClient.Builder builder = RestClient.builder(template);
		if (registry != null) {
			builder.observationRegistry(registry);
		}
		return builder;
	}

}
