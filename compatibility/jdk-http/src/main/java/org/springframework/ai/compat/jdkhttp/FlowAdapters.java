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

import reactor.core.publisher.Flux;

/**
 * @author 芋道源码
 */
public final class FlowAdapters {

	private FlowAdapters() {
	}

	public static <T> Flux<T> flowPublisherToFlux(Flow.Publisher<T> source) {
		return Flux.from(actual -> source.subscribe(new Flow.Subscriber<T>() {

			public void onSubscribe(Flow.Subscription s) {
				actual.onSubscribe(new org.reactivestreams.Subscription() {

					public void request(long n) {
						s.request(n);
					}

					public void cancel() {
						s.cancel();
					}
				});
			}

			public void onNext(T item) {
				actual.onNext(item);
			}

			public void onError(Throwable error) {
				actual.onError(error);
			}

			public void onComplete() {
				actual.onComplete();
			}
		}));
	}

	public static <T> Flow.Publisher<T> publisherToFlowPublisher(org.reactivestreams.Publisher<T> source) {
		return actual -> source.subscribe(new org.reactivestreams.Subscriber<T>() {

			public void onSubscribe(org.reactivestreams.Subscription s) {
				actual.onSubscribe(new Flow.Subscription() {

					public void request(long n) {
						s.request(n);
					}

					public void cancel() {
						s.cancel();
					}
				});
			}

			public void onNext(T item) {
				actual.onNext(item);
			}

			public void onError(Throwable error) {
				actual.onError(error);
			}

			public void onComplete() {
				actual.onComplete();
			}
		});
	}

}
