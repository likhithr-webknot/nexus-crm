package com.nexus.crm.shared.limits;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Allows at most {@code max} calls per key in each time window. The window starts at the first call.
 * Counts live in this server's memory, so with several servers each one counts on its own.
 */
public final class RateLimit {

	private final int max;

	private final Cache<String, AtomicInteger> counts;

	public RateLimit(int max, Duration window) {
		this.max = max;
		this.counts = Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).build();
	}

	/** Counts one call. Returns false when the key is over its limit. */
	public boolean tryAcquire(String key) {
		return this.counts.get(key, (k) -> new AtomicInteger()).incrementAndGet() <= this.max;
	}

	/** Counts one call, or answers 429 when the key is over its limit. */
	public void require(Object key) {
		if (!tryAcquire(String.valueOf(key))) {
			throw new ApiException(ErrorCode.RATE_LIMITED, "Too many requests. Wait a while and try again.");
		}
	}

}
