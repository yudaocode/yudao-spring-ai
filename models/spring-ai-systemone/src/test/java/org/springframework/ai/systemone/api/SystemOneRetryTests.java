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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.ai.systemone.api.exception.SystemOneApiConnectionException;
import org.springframework.ai.systemone.api.exception.SystemOneApiException;
import org.springframework.ai.systemone.api.exception.SystemOneApiResponseValidationException;
import org.springframework.ai.systemone.api.exception.SystemOneApiTimeoutException;
import org.springframework.ai.systemone.api.exception.SystemOneAuthenticationException;
import org.springframework.ai.systemone.api.exception.SystemOneBadRequestException;
import org.springframework.ai.systemone.api.exception.SystemOneInternalServerException;
import org.springframework.ai.systemone.api.exception.SystemOneRateLimitException;
import org.springframework.ai.systemone.api.exception.SystemOneUnprocessableEntityException;
import org.springframework.ai.systemone.api.question.Noul;
import org.springframework.ai.systemone.api.response.SystemOneResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.web.client.ExpectedCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;

/**
 * Retry behaviour. The waits are shrunk to a millisecond so the suite stays fast; what is
 * under test is which failures are repeated, how often, and that the server stated wait
 * wins, since the loop itself belongs to the Spring Framework {@link RetryTemplate}.
 *
 * @author Christian Tzolov
 */
class SystemOneRetryTests {

	private static final String OK_BODY = """
			{"model":"jev-1.13.0","answers":{"probe":{"type":"noul","noul":0.5}},"usage":{}}""";

	private static final RetryPolicy FAST_POLICY = SystemOneRetryUtils.retryPolicy(2, Duration.ofMillis(1),
			Duration.ofMillis(5), Duration.ofSeconds(30));

	private static final RetryTemplate FAST = SystemOneRetryUtils.retryTemplate(FAST_POLICY);

	private final MockSystemOneServer mock = MockSystemOneServer.create();

	@Test
	void retriesARateLimitAndSucceeds() {
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited"));
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		assertThat(evaluate(FAST).noulValue("probe")).isEqualTo(0.5);

		this.mock.server().verify();
	}

	@Test
	void retriesAServerErrorAndSucceeds() {
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(529, "overloaded"));
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		assertThat(evaluate(FAST).noulValue("probe")).isEqualTo(0.5);

		this.mock.server().verify();
	}

	@Test
	void stopsAfterMaxRetriesAndRethrowsTheLastFailure() {
		this.mock.server()
			.expect(ExpectedCount.times(3), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(500, "boom"));

		assertThatExceptionOfType(SystemOneInternalServerException.class).isThrownBy(() -> evaluate(FAST));

		this.mock.server().verify();
	}

	@Test
	void doesNotRetryAnAuthenticationFailure() {
		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(401, "bad key"));

		assertThatExceptionOfType(SystemOneAuthenticationException.class).isThrownBy(() -> evaluate(FAST));

		this.mock.server().verify();
	}

	@Test
	void doesNotRetryAValidationFailure() {
		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(422, "{\"detail\":\"questions required\"}"));

		assertThatExceptionOfType(SystemOneUnprocessableEntityException.class).isThrownBy(() -> evaluate(FAST));

		this.mock.server().verify();
	}

	@Test
	void noRetryTemplateFailsOnTheFirstAttempt() {
		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(500, "boom"));

		assertThatExceptionOfType(SystemOneInternalServerException.class)
			.isThrownBy(() -> evaluate(SystemOneRetryUtils.noRetryTemplate()));

		this.mock.server().verify();
	}

	@Test
	void theDefaultTemplateRetriesAServerError() {
		// The default waits are real, so one short-lived server error is enough to show
		// the default template retries and comes back with the answer.
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(503, "warming up"));
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		assertThat(evaluate(SystemOneRetryUtils.defaultRetryTemplate()).noulValue("probe")).isEqualTo(0.5);

		this.mock.server().verify();
	}

	@Test
	void honoursTheServerStatedWaitInsteadOfItsOwnBackoff() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(SystemOneRateLimitException.RETRY_AFTER_MS_HEADER, "40");

		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited", headers));
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		RetryTemplate honouring = SystemOneRetryUtils.retryTemplate(
				SystemOneRetryUtils.honouringRetryAfter(new org.springframework.retry.policy.SimpleRetryPolicy(3)));
		long startedAt = System.nanoTime();
		assertThat(evaluate(honouring).noulValue("probe")).isEqualTo(0.5);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

		// The policy's own wait is 1ms; the server asked for 40ms and that wins.
		assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(35));
		this.mock.server().verify();
	}

	@Test
	void theServerStatedWaitAppliesToTheFailureThatCarriedItOnly() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(SystemOneRateLimitException.RETRY_AFTER_MS_HEADER, "40");
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited", headers));
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(500, "temporary"));
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));
		java.util.List<Long> waits = new java.util.ArrayList<>();
		assertThat(evaluate(SystemOneRetryUtils.retryTemplate(FAST_POLICY, waits::add)).noulValue("probe"))
			.isEqualTo(0.5);
		assertThat(waits).hasSize(2);
		assertThat(waits.get(0)).isEqualTo(40L);
		assertThat(waits.get(1)).isLessThan(40L);
		this.mock.server().verify();
	}

	@Test
	void cutsAServerStatedWaitToTheTimeoutOfThePolicy() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(SystemOneRateLimitException.RETRY_AFTER_MS_HEADER, String.valueOf(Long.MAX_VALUE));
		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited", headers));
		java.util.List<Long> waits = new java.util.ArrayList<>();
		RetryPolicy policy = SystemOneRetryUtils.retryPolicy(2, Duration.ofMillis(1), Duration.ofMillis(5),
				Duration.ofMillis(200));
		assertThatExceptionOfType(SystemOneRateLimitException.class)
			.isThrownBy(() -> evaluate(SystemOneRetryUtils.retryTemplate(policy, waits::add)));
		assertThat(waits).isEmpty();
		this.mock.server().verify();
	}

	@Test
	@Timeout(10)
	void givesUpOnAServerStatedWaitTooLargeForTheClock() {
		// Added to the clock, this wait overflows the deadline check of the template,
		// which then sleeps for it instead of giving up.
		HttpHeaders headers = new HttpHeaders();
		headers.add(SystemOneRateLimitException.RETRY_AFTER_MS_HEADER, String.valueOf(Long.MAX_VALUE));

		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited", headers));

		RetryTemplate template = SystemOneRetryUtils.retryTemplate(
				SystemOneRetryUtils.retryPolicy(2, Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(200)));
		assertThatExceptionOfType(SystemOneRateLimitException.class).isThrownBy(() -> evaluate(template))
			.satisfies(ex -> assertThat(ex.retryAfterMs()).isEqualTo(Long.MAX_VALUE));

		this.mock.server().verify();
	}

	@Test
	@Timeout(10)
	void givesUpOnARetryAfterSecondsValueTooLargeForMilliseconds() {
		// Multiplied into milliseconds unchecked, this wraps to a negative wait that the
		// template fails on with an IllegalArgumentException in place of the rate limit.
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.RETRY_AFTER, "9300000000000000");

		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited", headers));

		RetryTemplate template = SystemOneRetryUtils.retryTemplate(
				SystemOneRetryUtils.retryPolicy(2, Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(200)));
		assertThatExceptionOfType(SystemOneRateLimitException.class).isThrownBy(() -> evaluate(template))
			.satisfies(ex -> assertThat(ex.retryAfterMs()).isEqualTo(Long.MAX_VALUE));

		this.mock.server().verify();
	}

	@Test
	void aPolicyWithoutATimeoutStopsOnAServerStatedWaitPastTheLimit() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(SystemOneRateLimitException.RETRY_AFTER_MS_HEADER,
				String.valueOf(SystemOneRetryUtils.MAX_SERVER_WAIT.toMillis() + 1));
		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited", headers));
		java.util.List<Long> waits = new java.util.ArrayList<>();
		RetryPolicy policy = SystemOneRetryUtils
			.honouringRetryAfter(new org.springframework.retry.policy.SimpleRetryPolicy(3));
		assertThatExceptionOfType(SystemOneRateLimitException.class)
			.isThrownBy(() -> evaluate(SystemOneRetryUtils.retryTemplate(policy, waits::add)));
		assertThat(waits).isEmpty();
		this.mock.server().verify();
	}

	@Test
	@Timeout(10)
	void aPolicyWithoutATimeoutGivesUpOnAServerStatedWaitOfADay() {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.RETRY_AFTER, "86400");

		this.mock.server()
			.expect(ExpectedCount.once(), requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.errorResponse(429, "rate limited", headers));

		RetryTemplate honouring = SystemOneRetryUtils.retryTemplate(
				SystemOneRetryUtils.honouringRetryAfter(new org.springframework.retry.policy.SimpleRetryPolicy(3)));
		assertThatExceptionOfType(SystemOneRateLimitException.class).isThrownBy(() -> evaluate(honouring))
			.satisfies(ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofDays(1)));

		this.mock.server().verify();
	}

	@Test
	void retriesAResponseThatBreaksOffWhileItIsRead() {
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.brokenBodyResponse(new IOException("Connection reset")));
		this.mock.server()
			.expect(requestTo(MockSystemOneServer.SYSTEM_ONE_URL))
			.andRespond(MockSystemOneServer.jsonResponse(OK_BODY));

		assertThat(evaluate(FAST).noulValue("probe")).isEqualTo(0.5);

		this.mock.server().verify();
	}

	@Test
	void classifiesConnectionFailuresAndRetryableStatusesAsTransient() {
		HttpHeaders headers = new HttpHeaders();

		assertThat(SystemOneRetryUtils.isTransient(new SystemOneApiConnectionException("down", null))).isTrue();
		assertThat(SystemOneRetryUtils.isTransient(new SystemOneApiTimeoutException("slow", null, null))).isTrue();
		assertThat(SystemOneRetryUtils.isTransient(new SystemOneApiException("408", 408, null, headers, "POST /")))
			.isTrue();
		assertThat(SystemOneRetryUtils
			.isTransient(new SystemOneRateLimitException("429", 429, null, headers, "POST /", null))).isTrue();
		assertThat(SystemOneRetryUtils
			.isTransient(new SystemOneInternalServerException("500", 500, null, headers, "POST /"))).isTrue();
		assertThat(SystemOneRetryUtils.isTransient(new SystemOneApiException("529", 529, null, headers, "POST /")))
			.isTrue();
	}

	@Test
	void classifiesClientErrorsAndBadResponsesAsPermanent() {
		HttpHeaders headers = new HttpHeaders();

		assertThat(SystemOneRetryUtils
			.isTransient(new SystemOneAuthenticationException("401", 401, null, headers, "POST /"))).isFalse();
		assertThat(
				SystemOneRetryUtils.isTransient(new SystemOneBadRequestException("400", 400, null, headers, "POST /")))
			.isFalse();
		assertThat(SystemOneRetryUtils
			.isTransient(new SystemOneApiResponseValidationException("no body", 200, null, headers, "POST /", null)))
			.isFalse();
		assertThat(SystemOneRetryUtils.isTransient(new IllegalStateException("unrelated"))).isFalse();
	}

	private SystemOneResponse evaluate(RetryTemplate retryTemplate) {
		return this.mock.client(retryTemplate).systemOne("anything", Map.of("probe", Noul.of("Is this a probe?")));
	}

}
