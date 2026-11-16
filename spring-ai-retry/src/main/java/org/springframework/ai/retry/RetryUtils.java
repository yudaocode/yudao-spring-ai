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

package org.springframework.ai.retry;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * RetryUtils is a utility class for configuring and handling retry operations. It
 * provides a default RetryTemplate and a default ResponseErrorHandler.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @since 0.8.1
 */
public abstract class RetryUtils {

	private static final int DEFAULT_MAX_ATTEMPTS = 10;

	private static final long DEFAULT_INITIAL_INTERVAL = 2000;

	private static final int DEFAULT_MULTIPLIER = 5;

	private static final long DEFAULT_MAX_INTERVAL = 3 * 60000;

	private static final long SHORT_INITIAL_INTERVAL = 100;

	private static final Log LOGGER = LogFactory.getLog(RetryUtils.class);

	/**
	 * Default ResponseErrorHandler implementation.
	 */
	public static final ResponseErrorHandler DEFAULT_RESPONSE_ERROR_HANDLER = new ResponseErrorHandler() {

		@Override
		public boolean hasError(final ClientHttpResponse response) throws IOException {
			return response.getStatusCode().isError();
		}

		@Override
		public void handleError(final URI url, final HttpMethod method, final ClientHttpResponse response)
				throws IOException {
			handleError(response);
		}

		@SuppressWarnings("removal")
		public void handleError(final ClientHttpResponse response) throws IOException {
			if (response.getStatusCode().isError()) {
				String error = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
				String message = String.format("%s - %s", response.getStatusCode().value(), error);
				/*
				 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided,
				 * 401 - You must be a member of an organization to use the API, 429 -
				 * Rate limit reached for requests, 429 - You exceeded your current quota,
				 * please check your plan and billing details.
				 */
				if (response.getStatusCode().is4xxClientError()) {
					throw new NonTransientAiException(message);
				}
				throw new TransientAiException(message);
			}
		}

	};

	/**
	 * Default RetryTemplate with exponential backoff configuration.
	 */
	public static final RetryTemplate DEFAULT_RETRY_TEMPLATE = createDefaultRetryTemplate();

	/**
	 * Short RetryTemplate for testing scenarios.
	 */
	public static final RetryTemplate SHORT_RETRY_TEMPLATE = createShortRetryTemplate();

	private static RetryTemplate createDefaultRetryTemplate() {
		return RetryTemplate.builder()
			.maxAttempts(DEFAULT_MAX_ATTEMPTS + 1)
			.retryOn(TransientAiException.class)
			.retryOn(ResourceAccessException.class)
			.exponentialBackoff(DEFAULT_INITIAL_INTERVAL, DEFAULT_MULTIPLIER, DEFAULT_MAX_INTERVAL)
			.withListener(loggingListener())
			.build();
	}

	private static RetryTemplate createShortRetryTemplate() {
		return RetryTemplate.builder()
			.maxAttempts(DEFAULT_MAX_ATTEMPTS + 1)
			.retryOn(TransientAiException.class)
			.retryOn(ResourceAccessException.class)
			.fixedBackoff(SHORT_INITIAL_INTERVAL)
			.withListener(loggingListener())
			.build();
	}

	private static RetryListener loggingListener() {
		return new RetryListener() {
			@Override
			public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				if (LOGGER.isWarnEnabled()) {
					LOGGER.warn("Retry error. Retry count:" + context.getRetryCount(), throwable);
				}
			}
		};
	}

	/**
	 * Generic execute method to run retryable operations with the provided RetryTemplate.
	 * @param <R> the return type
	 * @param retryTemplate the RetryTemplate to use for executing the retryable operation
	 * @param retryable the operation to be retried
	 * @return the result of the retryable operation
	 */
	public static <R extends @Nullable Object> R execute(RetryTemplate retryTemplate, Callable<R> retryable) {
		try {
			return retryTemplate.execute(context -> retryable.call());
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException("Retry operation failed", e);
		}
	}

}
