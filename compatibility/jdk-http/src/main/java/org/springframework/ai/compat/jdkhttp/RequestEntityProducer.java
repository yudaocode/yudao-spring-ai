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
import java.util.Collections;
import java.util.Set;

import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.apache.hc.core5.http.nio.DataStreamChannel;
import org.apache.hc.core5.reactive.ReactiveEntityProducer;

/**
 * Recreates the streaming producer for bodies that support a fresh subscription on retry.
 * Request bytes and Content-Type are already supplied by the caller, so no charset
 * parsing or body buffering is needed.
 *
 * @author 芋道源码
 */
final class RequestEntityProducer implements AsyncEntityProducer {

	private final HttpRequest.BodyPublisher body;

	private final String contentType;

	private final String encoding;

	private ReactiveEntityProducer delegate;

	RequestEntityProducer(HttpRequest.BodyPublisher body, String contentType, String encoding) {
		this.body = body;
		this.contentType = contentType;
		this.encoding = encoding;
	}

	private synchronized ReactiveEntityProducer delegate() {
		if (this.delegate == null) {
			this.delegate = new ReactiveEntityProducer(FlowAdapters.flowPublisherToFlux(this.body),
					this.body.contentLength(), null, this.encoding);
		}
		return this.delegate;
	}

	@Override
	public int available() {
		return delegate().available();
	}

	@Override
	public void produce(DataStreamChannel channel) throws IOException {
		delegate().produce(channel);
	}

	@Override
	public synchronized void releaseResources() {
		if (this.delegate != null) {
			this.delegate.releaseResources();
			this.delegate = null;
		}
	}

	@Override
	public boolean isRepeatable() {
		return this.body instanceof HttpRequest.RepeatableBodyPublisher;
	}

	@Override
	public void failed(Exception error) {
		releaseResources();
	}

	@Override
	public long getContentLength() {
		return this.body.contentLength();
	}

	@Override
	public String getContentType() {
		return this.contentType;
	}

	@Override
	public String getContentEncoding() {
		return this.encoding;
	}

	@Override
	public boolean isChunked() {
		return this.body.contentLength() < 0;
	}

	@Override
	public Set<String> getTrailerNames() {
		return Collections.emptySet();
	}

}
