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

package org.springframework.ai.retry.autoconfigure;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies retry counts and terminal exception identity across the framework migration.
 *
 * @author 芋道源码
 */
class Boot3RetryCompatibilityTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class))
		.withPropertyValues("spring.ai.retry.backoff.initial-interval=1ms", "spring.ai.retry.backoff.max-interval=2ms");

	@Test
	void zeroRetriesStillMakesTheInitialAttempt() {
		verifyAttempts(0, 1);
	}

	@Test
	void twoRetriesMakeThreeTotalAttempts() {
		verifyAttempts(2, 3);
	}

	private void verifyAttempts(int retries, int attempts) {
		this.contextRunner.withPropertyValues("spring.ai.retry.max-attempts=" + retries).run(context -> {
			assertThat(context).hasNotFailed();
			RetryTemplate retry = context.getBean(RetryTemplate.class);
			AtomicInteger calls = new AtomicInteger();
			TransientAiException failure = new TransientAiException("temporary");
			assertThatThrownBy(() -> retry.execute(state -> {
				calls.incrementAndGet();
				throw failure;
			})).isSameAs(failure);
			assertThat(calls).hasValue(attempts);
		});
	}

	@Test
	void nonTransientFailureIsPropagatedWithoutRetrying() {
		this.contextRunner.run(context -> {
			RetryTemplate retry = context.getBean(RetryTemplate.class);
			AtomicInteger calls = new AtomicInteger();
			NonTransientAiException failure = new NonTransientAiException("terminal");
			assertThatThrownBy(() -> retry.execute(state -> {
				calls.incrementAndGet();
				throw failure;
			})).isSameAs(failure);
			assertThat(calls).hasValue(1);
		});
	}

}
