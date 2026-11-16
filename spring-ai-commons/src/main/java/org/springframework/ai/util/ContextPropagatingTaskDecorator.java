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

import io.micrometer.context.ContextSnapshotFactory;

import org.springframework.core.task.TaskDecorator;
import org.springframework.util.Assert;

/**
 * Captures registered thread-local context and restores the worker's context after a
 * task.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class ContextPropagatingTaskDecorator implements TaskDecorator {

	private final ContextSnapshotFactory factory;

	public ContextPropagatingTaskDecorator() {
		this(ContextSnapshotFactory.builder().build());
	}

	public ContextPropagatingTaskDecorator(ContextSnapshotFactory factory) {
		Assert.notNull(factory, "Context snapshot factory must not be null");
		this.factory = factory;
	}

	@Override
	public Runnable decorate(Runnable runnable) {
		return this.factory.captureAll().wrap(runnable);
	}

}
