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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.util.ArrayList;

/**
 * Owns transports created by the Java 8 modern-client auto-configuration. The list is
 * destroyed by Spring and also closed if its construction fails before bean registration.
 *
 * @author 芋道源码
 */
final class ManagedModernMcpTransports extends ArrayList<NamedModernClientMcpTransport> implements AutoCloseable {

	private static final long serialVersionUID = 1L;

	private boolean closed;

	@Override
	public synchronized void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		RuntimeException failure = null;
		for (NamedModernClientMcpTransport named : this) {
			if (named.transport() instanceof AutoCloseable) {
				try {
					((AutoCloseable) named.transport()).close();
				}
				catch (Exception error) {
					if (error instanceof InterruptedException) {
						Thread.currentThread().interrupt();
					}
					IllegalStateException closeFailure = new IllegalStateException(
							"Failed to close modern MCP transport: " + named.name(), error);
					if (failure == null) {
						failure = closeFailure;
					}
					else {
						failure.addSuppressed(closeFailure);
					}
				}
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

}
