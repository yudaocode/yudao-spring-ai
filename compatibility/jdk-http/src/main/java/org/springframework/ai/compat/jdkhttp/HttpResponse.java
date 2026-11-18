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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

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

		default CompletionStage<T> apply(ResponseInfo response, Flow.Publisher<List<ByteBuffer>> publisher) {
			return apply(publisher);
		}

	}

	/**
	 * @author 芋道源码
	 */
	final class BodyHandlers {

		private BodyHandlers() {
		}

		public static <T> BodyHandler<T> ofResponseInfo(Function<ResponseInfo, BodyHandler<T>> handlerFactory) {
			Objects.requireNonNull(handlerFactory);
			return new BodyHandler<T>() {
				@Override
				public CompletionStage<T> apply(Flow.Publisher<List<ByteBuffer>> publisher) {
					throw new IllegalStateException("Response metadata is required");
				}

				@Override
				public CompletionStage<T> apply(ResponseInfo response, Flow.Publisher<List<ByteBuffer>> publisher) {
					return handlerFactory.apply(response).apply(response, publisher);
				}
			};
		}

		public static BodyHandler<Flow.Publisher<List<ByteBuffer>>> ofPublisher() {
			return CompletableFuture::completedFuture;
		}

		public static BodyHandler<java.io.InputStream> ofInputStream() {
			return publisher -> CompletableFuture.completedFuture(new PublisherInputStream(publisher));
		}

		/**
		 * Consume and discard all body buffers without retaining the response payload.
		 * @since 2.0.0
		 */
		public static BodyHandler<Void> discarding() {
			return publisher -> FlowAdapters.flowPublisherToFlux(publisher).then().toFuture();
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
			return ofString(StandardCharsets.UTF_8);
		}

		public static BodyHandler<String> ofString(Charset charset) {
			Objects.requireNonNull(charset);
			return publisher -> FlowAdapters.flowPublisherToFlux(publisher)
				.flatMapIterable(chunks -> chunks)
				.reduce(new java.io.ByteArrayOutputStream(), (out, buffer) -> {
					byte[] bytes = new byte[buffer.remaining()];
					buffer.get(bytes);
					out.write(bytes, 0, bytes.length);
					return out;
				})
				.map(out -> new String(out.toByteArray(), charset))
				.toFuture();
		}

	}

	/**
	 * Body handler adapters for SDKs that consume streaming text and map response bodies.
	 *
	 * @author 芋道源码
	 */
	final class BodySubscribers {

		private BodySubscribers() {
		}

		public static BodyHandler<String> ofString(Charset charset) {
			return BodyHandlers.ofString(charset);
		}

		public static <T, R> BodyHandler<R> mapping(BodyHandler<T> handler, Function<? super T, ? extends R> mapper) {
			return BodyHandlers.ofResponseInfo(response -> publisher -> handler.apply(response, publisher).thenApply(mapper));
		}

		public static BodyHandler<Void> fromLineSubscriber(Flow.Subscriber<? super String> subscriber) {
			Objects.requireNonNull(subscriber);
			return publisher -> {
				CompletableFuture<Void> completion = new CompletableFuture<>();
				Flux<String> lines = Flux.using(() -> new PublisherInputStream(publisher),
						input -> {
							BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
							return Flux.generate(sink -> {
								try {
									String line = reader.readLine();
									if (line == null) {
										sink.complete();
									}
									else {
										sink.next(line);
									}
								}
								catch (java.io.IOException ex) {
									sink.error(ex);
								}
							});
						}, PublisherInputStream::close);
				FlowAdapters.publisherToFlowPublisher(lines.subscribeOn(Schedulers.boundedElastic(), false))
					.subscribe(new Flow.Subscriber<String>() {
						@Override
						public void onSubscribe(Flow.Subscription subscription) {
							completion.whenComplete((ignored, error) -> {
								if (completion.isCancelled()) {
									subscription.cancel();
								}
							});
							subscriber.onSubscribe(new Flow.Subscription() {
								@Override
								public void request(long n) {
									subscription.request(n);
								}

								@Override
								public void cancel() {
									completion.cancel(false);
								}
							});
						}

						@Override
						public void onNext(String line) {
							subscriber.onNext(line);
						}

						@Override
						public void onError(Throwable error) {
							try {
								subscriber.onError(error);
							}
							finally {
								completion.completeExceptionally(error);
							}
						}

						@Override
						public void onComplete() {
							try {
								subscriber.onComplete();
								completion.complete(null);
							}
							catch (RuntimeException error) {
								completion.completeExceptionally(error);
							}
						}
					});
				return completion;
			};
		}

	}

}
