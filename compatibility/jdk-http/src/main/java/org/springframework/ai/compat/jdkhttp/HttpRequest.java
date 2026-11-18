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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;

/**
 * @author 芋道源码
 */
public final class HttpRequest {

	private final URI uri;

	private final String method;

	private final HttpHeaders headers;

	private final BodyPublisher body;

	private final Duration timeout;

	private final HttpClient.Version version;

	private final boolean expectContinue;

	private HttpRequest(Builder builder) {
		this.uri = Objects.requireNonNull(builder.uri, "Request URI is required");
		this.method = builder.method;
		this.headers = HttpHeaders.of(builder.headers, (k, v) -> true);
		this.body = builder.body;
		this.timeout = builder.timeout;
		this.version = builder.version;
		this.expectContinue = builder.expectContinue;
	}

	public URI uri() {
		return this.uri;
	}

	public String method() {
		return this.method;
	}

	public HttpHeaders headers() {
		return this.headers;
	}

	public Optional<BodyPublisher> bodyPublisher() {
		return Optional.ofNullable(this.body);
	}

	public Optional<Duration> timeout() {
		return Optional.ofNullable(this.timeout);
	}

	public Optional<HttpClient.Version> version() {
		return Optional.ofNullable(this.version);
	}

	public boolean expectContinue() {
		return this.expectContinue;
	}

	public static Builder newBuilder() {
		return new Builder();
	}

	public static Builder newBuilder(URI uri) {
		return new Builder().uri(uri);
	}

	public static Builder newBuilder(HttpRequest request, BiPredicate<String, String> headerFilter) {
		Objects.requireNonNull(request);
		Objects.requireNonNull(headerFilter);
		Builder builder = new Builder().uri(request.uri);
		builder.method = request.method;
		builder.body = request.body;
		builder.version = request.version;
		builder.timeout = request.timeout;
		builder.expectContinue = request.expectContinue;
		request.headers.map().forEach((name, values) -> values.forEach(value -> {
			if (headerFilter.test(name, value)) {
				builder.header(name, value);
			}
		}));
		return builder;
	}

	public interface BodyPublisher extends Flow.Publisher<ByteBuffer> {

		long contentLength();

	}

	/**
	 * A body whose publisher creates a fresh subscription for each transmission.
	 *
	 * @author 芋道源码
	 */
	interface RepeatableBodyPublisher extends BodyPublisher {

	}

	/**
	 * @author 芋道源码
	 */
	public static final class Builder {

		private URI uri;

		private String method = "GET";

		private Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

		private BodyPublisher body;

		private Duration timeout;

		private HttpClient.Version version;

		private boolean expectContinue;

		public Builder uri(URI uri) {
			Objects.requireNonNull(uri);
			if (!uri.isAbsolute()
					|| !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
				throw new IllegalArgumentException("An absolute HTTP(S) URI is required");
			}
			this.uri = uri;
			return this;
		}

		public Builder header(String name, String value) {
			validate(name, value);
			this.headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
			return this;
		}

		public Builder setHeader(String name, String value) {
			validate(name, value);
			this.headers.keySet().removeIf(previous -> previous.equalsIgnoreCase(name));
			this.headers.put(name, new ArrayList<>(Collections.singletonList(value)));
			return this;
		}

		public Builder headers(String... pairs) {
			if (pairs.length % 2 != 0) {
				throw new IllegalArgumentException("Expected header pairs");
			}
			for (int i = 0; i < pairs.length; i += 2) {
				header(pairs[i], pairs[i + 1]);
			}
			return this;
		}

		public Builder timeout(Duration timeout) {
			if (timeout.isNegative() || timeout.isZero()) {
				throw new IllegalArgumentException("Timeout must be positive");
			}
			this.timeout = timeout;
			return this;
		}

		public Builder version(HttpClient.Version version) {
			this.version = Objects.requireNonNull(version);
			return this;
		}

		public Builder expectContinue(boolean value) {
			this.expectContinue = value;
			return this;
		}

		public Builder method(String method, BodyPublisher body) {
			this.method = Objects.requireNonNull(method);
			this.body = Objects.requireNonNull(body);
			return this;
		}

		public Builder GET() {
			this.method = "GET";
			this.body = null;
			return this;
		}

		public Builder DELETE() {
			this.method = "DELETE";
			this.body = null;
			return this;
		}

		public Builder POST(BodyPublisher body) {
			return method("POST", body);
		}

		public Builder PUT(BodyPublisher body) {
			return method("PUT", body);
		}

		public HttpRequest build() {
			return new HttpRequest(this);
		}

		public Builder copy() {
			Builder b = new Builder();
			b.uri = this.uri;
			b.method = this.method;
			b.body = this.body;
			b.timeout = this.timeout;
			b.version = this.version;
			b.expectContinue = this.expectContinue;
			this.headers.forEach((k, v) -> b.headers.put(k, new ArrayList<>(v)));
			return b;
		}

		private static void validate(String name, String value) {
			Objects.requireNonNull(name);
			Objects.requireNonNull(value);
			if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
				throw new IllegalArgumentException("Invalid HTTP header");
			}
		}

	}

	/**
	 * @author 芋道源码
	 */
	public static final class BodyPublishers {

		private BodyPublishers() {
		}

		public static BodyPublisher ofInputStream(java.util.function.Supplier<? extends java.io.InputStream> source) {
			Objects.requireNonNull(source);
			return new RepeatableBodyPublisher() {

				public long contentLength() {
					return -1;
				}

				public void subscribe(Flow.Subscriber<? super ByteBuffer> actual) {
					reactor.core.publisher.Flux<ByteBuffer> chunks = reactor.core.publisher.Flux
						.using(() -> Objects.requireNonNull(source.get(), "Input stream supplier returned null"),
								input -> reactor.core.publisher.Flux.<ByteBuffer>generate(sink -> {
									try {
										byte[] bytes = new byte[8192];
										int count;
										do {
											count = input.read(bytes);
										}
										while (count == 0);
										if (count < 0) {
											sink.complete();
										}
										else {
											sink.next(ByteBuffer.wrap(bytes, 0, count).asReadOnlyBuffer());
										}
									}
									catch (java.io.IOException error) {
										sink.error(error);
									}
								}), input -> {
									try {
										input.close();
									}
									catch (java.io.IOException error) {
										throw new java.io.UncheckedIOException(error);
									}
								})
						.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
					FlowAdapters.publisherToFlowPublisher(chunks).subscribe(actual);
				}
			};
		}

		public static BodyPublisher noBody() {
			return ofByteArray(new byte[0]);
		}

		public static BodyPublisher ofString(String body) {
			return ofString(body, StandardCharsets.UTF_8);
		}

		public static BodyPublisher ofString(String body, Charset charset) {
			return ofByteArray(body.getBytes(charset));
		}

		public static BodyPublisher ofByteArray(byte[] body) {
			final byte[] bytes = body.clone();
			return new RepeatableBodyPublisher() {

				public long contentLength() {
					return bytes.length;
				}

				public void subscribe(Flow.Subscriber<? super ByteBuffer> actual) {
					actual.onSubscribe(new Flow.Subscription() {

						private final AtomicBoolean done = new AtomicBoolean();

						private volatile boolean cancelled;

						public void request(long n) {
							if (this.done.compareAndSet(false, true) && !this.cancelled) {
								if (n <= 0) {
									actual.onError(new IllegalArgumentException("Demand must be positive"));
									return;
								}
								if (bytes.length > 0) {
									actual.onNext(ByteBuffer.wrap(bytes).asReadOnlyBuffer());
								}
								if (!this.cancelled) {
									actual.onComplete();
								}
							}
						}

						public void cancel() {
							this.cancelled = true;
						}
					});
				}
			};
		}

	}

}
