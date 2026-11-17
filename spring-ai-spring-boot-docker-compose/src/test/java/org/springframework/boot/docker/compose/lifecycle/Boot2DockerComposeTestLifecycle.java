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

package org.springframework.boot.docker.compose.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationShutdownHandlers;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Executes real Compose shutdown callbacks in tests without casting Boot2 internals. Real
 * process-exit shutdown is validated by the separate native8 child-VM test.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class Boot2DockerComposeTestLifecycle {

	private static final Set<Runnable> callbacks = new CopyOnWriteArraySet<>();

	private static final Set<ConfigurableApplicationContext> contexts = new CopyOnWriteArraySet<>();

	private static final SpringApplicationShutdownHandlers handlers = new SpringApplicationShutdownHandlers() {
		public void add(Runnable action) {
			callbacks.add(java.util.Objects.requireNonNull(action));
		}

		public void remove(Runnable action) {
			callbacks.remove(action);
		}
	};

	private Boot2DockerComposeTestLifecycle() {
	}

	/**
	 * Give this test application its own callback registry.
	 * @param application the test application
	 * @since 2.1.0
	 */
	public static void configure(SpringApplication application) {
		List<ApplicationListener<?>> listeners = new ArrayList<>();
		for (ApplicationListener<?> listener : application.getListeners()) {
			listeners.add(listener instanceof DockerComposeListener ? new DockerComposeListener(handlers) : listener);
		}
		application.setListeners(listeners);
	}

	/**
	 * Track a context owned by the current tests.
	 * @param context the application context
	 * @return the same context
	 * @since 2.1.0
	 */
	public static ConfigurableApplicationContext track(ConfigurableApplicationContext context) {
		contexts.add(context);
		return context;
	}

	/**
	 * Close owned contexts and run every real stop callback once.
	 * @since 2.1.0
	 */
	public static void shutdown() {
		RuntimeException failure = null;
		for (ConfigurableApplicationContext context : new ArrayList<>(contexts)) {
			contexts.remove(context);
			try {
				context.close();
			}
			catch (RuntimeException ex) {
				if (failure == null) {
					failure = ex;
				}
				else {
					failure.addSuppressed(ex);
				}
			}
		}
		for (Runnable action : new ArrayList<>(callbacks)) {
			callbacks.remove(action);
			try {
				action.run();
			}
			catch (RuntimeException ex) {
				if (failure == null) {
					failure = ex;
				}
				else {
					failure.addSuppressed(ex);
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

}
