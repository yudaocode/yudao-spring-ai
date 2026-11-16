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

package org.springframework.ai.compat.jdkhttp;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author 芋道源码
 */
public interface HttpResponse<T> {

	int statusCode();

	HttpHeaders headers();

	T body();

	HttpClient.Version version();

	URI uri();

	HttpRequest request();

	interface ResponseInfo {

		int statusCode();

		HttpHeaders headers();

		HttpClient.Version version();

	}

	interface BodyHandler<T> {

		CompletionStage<T> apply(Flow.Publisher<List<ByteBuffer>> publisher);

	}

	/**
	 * @author 芋道源码
	 */
	final class BodyHandlers {

		private BodyHandlers() {
		}

		public static BodyHandler<Flow.Publisher<List<ByteBuffer>>> ofPublisher() {
			return CompletableFuture::completedFuture;
		}

		public static BodyHandler<java.io.InputStream> ofInputStream() {
			return publisher -> CompletableFuture.completedFuture(new PublisherInputStream(publisher));
		}

		public static BodyHandler<byte[]> ofByteArray() {
			return publisher -> FlowAdapters.flowPublisherToFlux(publisher)
				.flatMapIterable(chunks -> chunks)
				.reduce(new java.io.ByteArrayOutputStream(), (out, buffer) -> {
					byte[] bytes = new byte[buffer.remaining()];
					buffer.get(bytes);
					out.write(bytes, 0, bytes.length);
					return out;
				})
				.map(java.io.ByteArrayOutputStream::toByteArray)
				.toFuture();
		}

		public static BodyHandler<String> ofString() {
			return publisher -> FlowAdapters.flowPublisherToFlux(publisher)
				.flatMapIterable(chunks -> chunks)
				.reduce(new java.io.ByteArrayOutputStream(), (out, buffer) -> {
					byte[] bytes = new byte[buffer.remaining()];
					buffer.get(bytes);
					out.write(bytes, 0, bytes.length);
					return out;
				})
				.map(out -> new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8))
				.toFuture();
		}

	}

}
