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

package org.springframework.ai.compat.observation;

import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author 芋道源码
 */
class ObservationCompatibilityTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class));

	@Test
	void retainsUserRegistryAndAppliesCustomizerAndHandler() {
		this.runner.withUserConfiguration(UserRegistry.class).run(context -> {
			ObservationRegistry registry = context.getBean(ObservationRegistry.class);
			assertThat(registry).isSameAs(context.getBean(UserRegistry.class).registry);
			Observation observation = Observation.start("compat.enabled", registry);
			observation.stop();
			assertThat(context.getBean(AtomicInteger.class)).hasValue(1);
			assertThat(observation.getContext().getOrDefault("customized", false)).isEqualTo(true);
		});
	}

	@Test
	void propertyPredicateDisablesObservationsWithoutDiscardingUserRegistry() {
		this.runner.withUserConfiguration(UserRegistry.class)
			.withPropertyValues("management.observations.enable.compat=false")
			.run(context -> {
				Observation.start("compat.disabled", context.getBean(ObservationRegistry.class)).stop();
				assertThat(context.getBean(AtomicInteger.class)).hasValue(0);
			});
	}

	@Test
	void metersRunWhenTracingClassesAreAbsent() {
		this.runner.withClassLoader(new FilteredClassLoader("io.micrometer.tracing"))
			.withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
			.run(context -> {
				Observation.start("compat.runtime", context.getBean(ObservationRegistry.class)).stop();
				assertThat(context.getBean(SimpleMeterRegistry.class).get("compat.runtime").timer().count())
					.isEqualTo(1);
			});
	}

	/**
	 * @author 芋道源码
	 */
	@Configuration(proxyBeanMethods = false)
	static class UserRegistry {

		final ObservationRegistry registry = ObservationRegistry.create();

		@Bean
		ObservationRegistry userRegistry() {
			return this.registry;
		}

		@Bean
		AtomicInteger stops() {
			return new AtomicInteger();
		}

		@Bean
		ObservationHandler<Observation.Context> handler(AtomicInteger stops) {
			return new ObservationHandler<Observation.Context>() {

				public boolean supportsContext(Observation.Context context) {
					return true;
				}

				public void onStop(Observation.Context context) {
					stops.incrementAndGet();
				}
			};
		}

		@Bean
		ObservationRegistryCustomizer<ObservationRegistry> customizer() {
			return registry -> registry.observationConfig().observationFilter(context -> {
				context.put("customized", true);
				return context;
			});
		}

	}

}
