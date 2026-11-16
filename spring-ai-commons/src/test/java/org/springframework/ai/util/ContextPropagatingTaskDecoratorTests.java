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

package org.springframework.ai.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author 芋道源码
 */
class ContextPropagatingTaskDecoratorTests {

	@Test
	void capturesCallerAndRestoresWorkerAfterSuccessAndFailure() throws Exception {
		ThreadLocal<String> local = new ThreadLocal<>();
		ContextRegistry registry = new ContextRegistry().registerThreadLocalAccessor("java8-context", local::get,
				local::set, local::remove);
		ContextPropagatingTaskDecorator decorator = new ContextPropagatingTaskDecorator(
				ContextSnapshotFactory.builder().contextRegistry(registry).build());
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			executor.submit(() -> local.set("worker")).get(5, TimeUnit.SECONDS);
			local.set("caller");
			for (boolean fail : new boolean[] { false, true }) {
				Runnable task = decorator.decorate(() -> {
					assertThat(local.get()).isEqualTo("caller");
					local.set("temporary");
					if (fail) {
						throw new IllegalStateException("expected");
					}
				});
				if (fail) {
					assertThatThrownBy(() -> executor.submit(task).get(5, TimeUnit.SECONDS))
						.hasCauseInstanceOf(IllegalStateException.class);
				}
				else {
					executor.submit(task).get(5, TimeUnit.SECONDS);
				}
				assertThat(executor.submit(local::get).get(5, TimeUnit.SECONDS)).isEqualTo("worker");
				assertThat(local.get()).isEqualTo("caller");
			}
		}
		finally {
			local.remove();
			executor.shutdownNow();
		}
	}

}
