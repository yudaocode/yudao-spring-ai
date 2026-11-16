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

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A bounded, cancellable blocking view of an HTTP response publisher. Requests one batch
 * at a time and wakes a blocked reader on close.
 *
 * @author 芋道源码
 */
final class PublisherInputStream extends InputStream {

	private static final Object COMPLETE = new Object();

	private static final Object CLOSED = new Object();

	private final Flow.Publisher<List<ByteBuffer>> body;

	private final Object initialization = new Object();

	private final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(2);

	private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();

	private volatile boolean closed;

	private boolean initialized;

	private boolean receivedBatch;

	private boolean end;

	private Iterator<ByteBuffer> buffers = Collections.emptyIterator();

	private ByteBuffer current;

	PublisherInputStream(Flow.Publisher<List<ByteBuffer>> body) {
		this.body = Objects.requireNonNull(body);
	}

	private void initialize() throws IOException {
		synchronized (this.initialization) {
			if (this.closed) {
				throw new IOException("Response body is closed");
			}
			if (this.initialized) {
				return;
			}
			this.initialized = true;
			try {
				this.body.subscribe(new Flow.Subscriber<List<ByteBuffer>>() {

					public void onSubscribe(Flow.Subscription value) {
						if (!subscription.compareAndSet(null, value) || closed) {
							value.cancel();
						}
						else {
							value.request(1);
						}
					}

					public void onNext(List<ByteBuffer> value) {
						if (!closed && !queue.offer(value)) {
							subscription.get().cancel();
							queue.clear();
							queue.offer(new Failure(new IOException("Response publisher exceeded demand")));
						}
					}

					public void onError(Throwable error) {
						if (!closed) {
							queue.offer(new Failure(error));
						}
					}

					public void onComplete() {
						if (!closed) {
							queue.offer(COMPLETE);
						}
					}
				});
			}
			catch (RuntimeException error) {
				close();
				throw failure(error);
			}
		}
	}

	@Override
	public int read() throws IOException {
		byte[] value = new byte[1];
		return read(value, 0, 1) == -1 ? -1 : value[0] & 255;
	}

	@Override
	public int read(byte[] bytes, int offset, int length) throws IOException {
		Objects.requireNonNull(bytes);
		if (offset < 0 || length < 0 || offset > bytes.length - length) {
			throw new IndexOutOfBoundsException();
		}
		if (this.closed) {
			throw new IOException("Response body is closed");
		}
		if (length == 0) {
			return 0;
		}
		if (this.end) {
			return -1;
		}
		initialize();
		while (this.current == null || !this.current.hasRemaining()) {
			if (this.buffers.hasNext()) {
				this.current = this.buffers.next();
				continue;
			}
			if (this.receivedBatch) {
				this.receivedBatch = false;
				this.subscription.get().request(1);
			}
			Object signal;
			try {
				signal = this.queue.take();
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				close();
				InterruptedIOException failure = new InterruptedIOException("HTTP body read interrupted");
				failure.initCause(interrupted);
				throw failure;
			}
			if (signal == CLOSED || this.closed) {
				throw new IOException("Response body is closed");
			}
			if (signal == COMPLETE) {
				this.end = true;
				return -1;
			}
			if (signal instanceof Failure) {
				close();
				throw failure(((Failure) signal).error);
			}
			@SuppressWarnings("unchecked")
			List<ByteBuffer> batch = (List<ByteBuffer>) signal;
			this.buffers = batch.iterator();
			this.receivedBatch = true;
		}
		int copied = Math.min(length, this.current.remaining());
		this.current.get(bytes, offset, copied);
		return copied;
	}

	@Override
	public void close() {
		synchronized (this.initialization) {
			if (this.closed) {
				return;
			}
			this.closed = true;
			Flow.Subscription active = this.subscription.get();
			if (active != null) {
				active.cancel();
			}
			if (!this.initialized) {
				this.initialized = true;
				this.body.subscribe(new Flow.Subscriber<List<ByteBuffer>>() {

					public void onSubscribe(Flow.Subscription value) {
						value.cancel();
					}

					public void onNext(List<ByteBuffer> ignored) {
					}

					public void onError(Throwable ignored) {
					}

					public void onComplete() {
					}
				});
			}
			this.queue.clear();
			this.queue.offer(CLOSED);
		}
	}

	private static IOException failure(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof IOException) {
				return (IOException) cause;
			}
		}
		return new IOException("Could not read HTTP response body", error);
	}

	/**
	 * @author 芋道源码
	 */
	private static final class Failure {

		private final Throwable error;

		private Failure(Throwable error) {
			this.error = error;
		}

	}

}
