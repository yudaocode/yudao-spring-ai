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

/**
 * Java 8 reactive-stream contracts for the MCP transport bridge.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class Flow {

	private Flow() {
	}

	public interface Publisher<T> {

		void subscribe(Subscriber<? super T> subscriber);

	}

	public interface Subscriber<T> {

		void onSubscribe(Subscription subscription);

		void onNext(T item);

		void onError(Throwable error);

		void onComplete();

	}

	/**
	 * @author 芋道源码
	 */
	public interface Processor<T, R> extends Subscriber<T>, Publisher<R> {

	}

	public interface Subscription {

		void request(long amount);

		void cancel();

	}

}
