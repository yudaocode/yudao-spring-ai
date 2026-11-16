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

package org.springframework.ai.systemone.api;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.systemone.api.exception.SystemOneApiConnectionException;
import org.springframework.ai.systemone.api.exception.SystemOneApiException;
import org.springframework.ai.systemone.api.exception.SystemOneRateLimitException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.Sleeper;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * System One retry defaults implemented with the Spring Retry version managed by Boot 3.
 * Retry state, deadlines and server-directed delays belong to each invocation.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SystemOneRetryUtils {

	private static final Log logger = LogFactory.getLog(SystemOneRetryUtils.class);

	public static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 429);

	public static final int DEFAULT_MAX_RETRIES = 2;

	public static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(500);

	public static final Duration DEFAULT_MAX_BACKOFF = Duration.ofSeconds(5);

	public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	public static final Duration MAX_SERVER_WAIT = Duration.ofMinutes(1);

	public static final RetryPolicy DEFAULT_RETRY_POLICY = retryPolicy(DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_BACKOFF,
			DEFAULT_MAX_BACKOFF, DEFAULT_TIMEOUT);

	public static final RetryPolicy NO_RETRY_POLICY = new TransientRetryPolicy(0, Duration.ZERO);

	private SystemOneRetryUtils() {
	}

	public static RetryPolicy retryPolicy(int maxRetries, Duration initialBackoff, Duration maxBackoff,
			Duration timeout) {
		Assert.isTrue(maxRetries >= 0 && maxRetries < Integer.MAX_VALUE, "maxRetries must be non-negative and bounded");
		Assert.notNull(initialBackoff, "initialBackoff must not be null");
		Assert.notNull(maxBackoff, "maxBackoff must not be null");
		Assert.notNull(timeout, "timeout must not be null");
		Assert.isTrue(!initialBackoff.isNegative(), "initialBackoff must not be negative");
		Assert.isTrue(maxBackoff.compareTo(initialBackoff) >= 0 && !maxBackoff.isZero(),
				"maxBackoff must be positive and at least initialBackoff");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "timeout must be positive");
		return new RetryAfterPolicy(new TransientRetryPolicy(maxRetries, timeout), initialBackoff, maxBackoff, timeout);
	}

	public static RetryTemplate defaultRetryTemplate() {
		return retryTemplate(DEFAULT_RETRY_POLICY);
	}

	public static RetryTemplate retryTemplate(RetryPolicy policy) {
		return retryTemplate(policy, Thread::sleep);
	}

	/**
	 * Creates a retry template with an application-provided sleeper.
	 * @param policy the retry policy
	 * @param sleeper the wait implementation
	 * @return the configured template
	 * @since 2.1.0
	 */
	public static RetryTemplate retryTemplate(RetryPolicy policy, Sleeper sleeper) {
		Assert.notNull(policy, "policy must not be null");
		Assert.notNull(sleeper, "sleeper must not be null");
		RetryTemplate template = new RetryTemplate();
		template.setRetryPolicy(policy);
		RetryAfterPolicy delays = policy instanceof RetryAfterPolicy configured ? configured
				: new RetryAfterPolicy(policy, DEFAULT_INITIAL_BACKOFF, DEFAULT_MAX_BACKOFF, Duration.ZERO);
		template.setBackOffPolicy(delays.backOffPolicy(sleeper));
		template.registerListener(new RetryListener() {
			@Override
			public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
					Throwable throwable) {
				if (logger.isWarnEnabled() && policy.canRetry(context)) {
					logger.warn("System One call failed, retry " + context.getRetryCount(), throwable);
				}
			}
		});
		return template;
	}

	public static RetryTemplate noRetryTemplate() {
		return retryTemplate(NO_RETRY_POLICY);
	}

	public static boolean isTransient(Throwable throwable) {
		if (throwable instanceof SystemOneApiException apiException) {
			int status = apiException.status();
			return RETRYABLE_STATUSES.contains(status) || status >= 500;
		}
		return throwable instanceof SystemOneApiConnectionException;
	}

	public static RetryPolicy honouringRetryAfter(RetryPolicy policy) {
		Assert.notNull(policy, "policy must not be null");
		if (policy instanceof RetryAfterPolicy) {
			return policy;
		}
		return new RetryAfterPolicy(policy, DEFAULT_INITIAL_BACKOFF, DEFAULT_MAX_BACKOFF, Duration.ZERO);
	}

	private static final class TransientRetryPolicy extends SimpleRetryPolicy {

		private static final String START = TransientRetryPolicy.class.getName() + ".start";

		private final long timeoutNanos;

		TransientRetryPolicy(int maxRetries, Duration timeout) {
			super(maxRetries + 1);
			this.timeoutNanos = timeout.toNanos();
		}

		@Override
		public RetryContext open(RetryContext parent) {
			RetryContext context = super.open(parent);
			context.setAttribute(START, System.nanoTime());
			return context;
		}

		@Override
		public boolean canRetry(RetryContext context) {
			Throwable failure = context.getLastThrowable();
			long elapsed = System.nanoTime() - (Long) java.util.Objects.requireNonNull(context.getAttribute(START));
			return !context.isExhaustedOnly() && super.canRetry(context) && (failure == null || isTransient(failure))
					&& (this.timeoutNanos == 0 || elapsed < this.timeoutNanos);
		}

	}

	private static final class RetryAfterPolicy implements RetryPolicy {

		private final RetryPolicy delegate;

		private final long initialDelay;

		private final long maxDelay;

		private final long timeoutNanos;

		RetryAfterPolicy(RetryPolicy delegate, Duration initialBackoff, Duration maxBackoff, Duration timeout) {
			this.delegate = delegate;
			this.initialDelay = initialBackoff.toMillis();
			this.maxDelay = maxBackoff.toMillis();
			this.timeoutNanos = timeout.toNanos();
		}

		@Override
		public RetryContext open(RetryContext parent) {
			return this.delegate.open(parent);
		}

		@Override
		public boolean canRetry(RetryContext context) {
			return !context.isExhaustedOnly() && this.delegate.canRetry(context);
		}

		@Override
		public void registerThrowable(RetryContext context, Throwable throwable) {
			this.delegate.registerThrowable(context, throwable);
		}

		@Override
		public void close(RetryContext context) {
			this.delegate.close(context);
		}

		BackOffPolicy backOffPolicy(Sleeper sleeper) {
			return new BackOffPolicy() {
				@Override
				public BackOffContext start(RetryContext context) {
					return new DelayState(context, initialDelay);
				}

				@Override
				public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
					DelayState state = (DelayState) backOffContext;
					long wait = state.delay;
					state.delay = wait >= maxDelay / 2 ? maxDelay : Math.min(maxDelay, wait * 2);
					Throwable failure = state.context.getLastThrowable();
					boolean serverDirected = failure instanceof SystemOneRateLimitException rateLimit
							&& rateLimit.retryAfter() != null;
					if (serverDirected) {
						Duration retryAfter = java.util.Objects.requireNonNull(
								((SystemOneRateLimitException) java.util.Objects.requireNonNull(failure)).retryAfter());
						try {
							wait = retryAfter.toMillis();
						}
						catch (ArithmeticException ex) {
							state.context.setExhaustedOnly();
							return;
						}
					}
					else {
						long jitter = Math.min(100, initialDelay / 5);
						wait = Math.max(0, wait + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1));
					}
					long remaining = timeoutNanos - (System.nanoTime() - state.started);
					if ((timeoutNanos > 0 && (remaining <= 0 || wait >= remaining / 1_000_000))
							|| (timeoutNanos == 0 && serverDirected && wait > MAX_SERVER_WAIT.toMillis())) {
						state.context.setExhaustedOnly();
						return;
					}
					try {
						sleeper.sleep(Math.max(0, wait));
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						throw new BackOffInterruptedException("Interrupted waiting to retry System One", ex);
					}
				}
			};
		}

	}

	private static final class DelayState implements BackOffContext {

		private final RetryContext context;

		private final long started = System.nanoTime();

		private long delay;

		DelayState(RetryContext context, long delay) {
			this.context = context;
			this.delay = delay;
		}

	}

}
