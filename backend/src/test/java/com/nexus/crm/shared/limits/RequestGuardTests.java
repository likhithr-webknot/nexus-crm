package com.nexus.crm.shared.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

class RequestGuardTests {

	private final HandlerExceptionResolver errors = mock(HandlerExceptionResolver.class);

	private final FilterChain chain = mock(FilterChain.class);

	@Test
	void aLimitAllowsTheMaximumAndThenRefuses() {
		RateLimit limit = new RateLimit(3, Duration.ofMinutes(1));

		assertThat(limit.tryAcquire("a")).isTrue();
		assertThat(limit.tryAcquire("a")).isTrue();
		assertThat(limit.tryAcquire("a")).isTrue();
		assertThat(limit.tryAcquire("a")).isFalse();
		// Another key has its own count.
		assertThat(limit.tryAcquire("b")).isTrue();
	}

	@Test
	void requireAnswersWithTheRateLimitedCode() {
		RateLimit limit = new RateLimit(1, Duration.ofMinutes(1));
		limit.require("user");

		assertThatThrownBy(() -> limit.require("user")).isInstanceOfSatisfying(ApiException.class,
				(ex) -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RATE_LIMITED));
	}

	@Test
	void refreshIsLimitedPerAddress() throws Exception {
		AuthRateLimitFilter filter = new AuthRateLimitFilter(this.errors, "https://app.example");
		for (int i = 0; i < 120; i++) {
			filter.doFilter(refresh("203.0.113.5"), new MockHttpServletResponse(), this.chain);
		}
		verify(this.errors, never()).resolveException(any(), any(), any(), any());

		filter.doFilter(refresh("203.0.113.5"), new MockHttpServletResponse(), this.chain);

		ArgumentCaptor<Exception> refused = ArgumentCaptor.forClass(Exception.class);
		verify(this.errors).resolveException(any(), any(), isNull(), refused.capture());
		assertThat(((ApiException) refused.getValue()).getCode()).isEqualTo(ErrorCode.RATE_LIMITED);
		// A different address is not affected.
		filter.doFilter(refresh("203.0.113.6"), new MockHttpServletResponse(), this.chain);
		verify(this.chain, org.mockito.Mockito.times(121)).doFilter(any(), any());
	}

	@Test
	void signInFloodIsSentBackToTheLoginPage() throws Exception {
		AuthRateLimitFilter filter = new AuthRateLimitFilter(this.errors, "https://app.example");
		MockHttpServletResponse last = new MockHttpServletResponse();
		for (int i = 0; i < 61; i++) {
			last = new MockHttpServletResponse();
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/oauth2/authorization/google");
			request.setRemoteAddr("203.0.113.9");
			filter.doFilter(request, last, this.chain);
		}

		assertThat(last.getRedirectedUrl()).isEqualTo("https://app.example/login?error=rate_limited");
	}

	@Test
	void aBodyOverTheLimitIsRefusedBeforeItIsRead() throws Exception {
		BodySizeLimitFilter filter = new BodySizeLimitFilter(this.errors);
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/workspaces/quotes");
		request.setContentType("application/json");
		request.setContent(new byte[1_000_001]);

		filter.doFilter(request, new MockHttpServletResponse(), this.chain);

		ArgumentCaptor<Exception> refused = ArgumentCaptor.forClass(Exception.class);
		verify(this.errors).resolveException(any(), any(), isNull(), refused.capture());
		assertThat(((ApiException) refused.getValue()).getCode()).isEqualTo(ErrorCode.TOO_LARGE);
		verify(this.chain, never()).doFilter(any(), any());
	}

	@Test
	void importsAndExportsMayBeBiggerThanOrdinaryRequests() {
		assertThat(BodySizeLimitFilter.limitFor("/api/v1/workspaces/quotes")).isEqualTo(1_000_000);
		assertThat(BodySizeLimitFilter.limitFor("/api/v1/leads/imports")).isGreaterThan(10_000_000);
		assertThat(BodySizeLimitFilter.limitFor("/api/v1/exports/sheet")).isGreaterThan(10_000_000);
	}

	@Test
	void aBodyThatStreamsPastTheLimitStopsBeingRead() throws Exception {
		BodySizeLimitFilter filter = new BodySizeLimitFilter(this.errors);
		// No Content-Length, as with a chunked upload.
		MockHttpServletRequest chunked = new MockHttpServletRequest("POST", "/api/v1/workspaces/quotes") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		chunked.setContentType("application/json");
		chunked.setContent(new byte[1_000_100]);
		FilterChain reading = (request, response) -> assertThatThrownBy(() -> request.getInputStream().readAllBytes())
			.isInstanceOf(java.io.IOException.class);

		filter.doFilter(chunked, new MockHttpServletResponse(), reading);
	}

	private static MockHttpServletRequest refresh(String address) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
		request.setRemoteAddr(address);
		return request;
	}

}
