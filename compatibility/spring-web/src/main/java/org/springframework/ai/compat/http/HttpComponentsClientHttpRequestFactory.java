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

package org.springframework.ai.compat.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.io.ModalCloseable;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;

/**
 * Connects Spring 5 to an explicitly configured HttpClient 5 instance. Preserves the
 * supplied client's DNS, TLS and redirect policies for bounded media fetches.
 *
 * @author 芋道源码
 * @since 2.1.0
 */
public final class HttpComponentsClientHttpRequestFactory implements ClientHttpRequestFactory {

	private final HttpClient client;

	public HttpComponentsClientHttpRequestFactory(HttpClient client) {
		Assert.notNull(client, "HttpClient must not be null");
		this.client = client;
	}

	@Override
	public ClientHttpRequest createRequest(URI uri, HttpMethod method) {
		return new Request(this.client, uri, method);
	}

	/**
	 * @author 芋道源码
	 */
	private static final class Request implements ClientHttpRequest {

		private final HttpClient client;

		private final URI uri;

		private final HttpMethod method;

		private final HttpHeaders headers = new HttpHeaders();

		private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

		private boolean executed;

		private Request(HttpClient client, URI uri, HttpMethod method) {
			this.client = client;
			this.uri = uri;
			this.method = method;
		}

		@Override
		public HttpMethod getMethod() {
			return this.method;
		}

		@Override
		public String getMethodValue() {
			return this.method.name();
		}

		@Override
		public URI getURI() {
			return this.uri;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.executed ? HttpHeaders.readOnlyHttpHeaders(this.headers) : this.headers;
		}

		@Override
		public java.io.OutputStream getBody() {
			Assert.state(!this.executed, "Request has already been executed");
			return this.output;
		}

		@Override
		public ClientHttpResponse execute() throws IOException {
			Assert.state(!this.executed, "Request has already been executed");
			this.executed = true;
			return executeInternal(this.headers, this.output.toByteArray());
		}

		private ClientHttpResponse executeInternal(HttpHeaders headers, byte[] body) throws IOException {
			HttpUriRequestBase request = new HttpUriRequestBase(this.method.name(), this.uri);
			headers.forEach((name, values) -> {
				if (!HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)
						&& !HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)) {
					values.forEach(value -> request.addHeader(name, value));
				}
			});
			if (body.length != 0 || this.method == HttpMethod.POST || this.method == HttpMethod.PUT
					|| this.method == HttpMethod.PATCH) {
				String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
				request
					.setEntity(new ByteArrayEntity(body, contentType == null ? null : ContentType.parse(contentType)));
			}
			return new Response(this.client.executeOpen(null, request, null));
		}

	}

	/**
	 * @author 芋道源码
	 */
	private static final class Response implements ClientHttpResponse {

		private final ClassicHttpResponse response;

		private final HttpHeaders headers = new HttpHeaders();

		private Response(ClassicHttpResponse response) {
			this.response = response;
			for (org.apache.hc.core5.http.Header header : response.getHeaders()) {
				this.headers.add(header.getName(), header.getValue());
			}
		}

		@Override
		public HttpStatus getStatusCode() {
			return HttpStatus.valueOf(this.response.getCode());
		}

		@Override
		public int getRawStatusCode() {
			return this.response.getCode();
		}

		@Override
		public String getStatusText() {
			String reason = this.response.getReasonPhrase();
			return reason == null ? "" : reason;
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.headers;
		}

		@Override
		public InputStream getBody() throws IOException {
			HttpEntity entity = this.response.getEntity();
			return entity == null ? new java.io.ByteArrayInputStream(new byte[0]) : entity.getContent();
		}

		@Override
		public void close() {
			try {
				// Abort unread data instead of draining beyond the caller's download
				// limit.
				if (this.response instanceof ModalCloseable) {
					((ModalCloseable) this.response).close(CloseMode.IMMEDIATE);
				}
				else {
					this.response.close();
				}
			}
			catch (IOException ignored) {
			}
		}

	}

}
