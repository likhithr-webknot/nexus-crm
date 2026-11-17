package com.nexus.crm.shared.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/**
 * Makes a create request safe to repeat. A browser that sends the same request twice (a double click, a retry after a
 * dropped connection) gets the first answer back instead of creating a second record.
 *
 * <p>How it works: the client sends an {@code Idempotency-Key} header. The key is saved in the same database transaction
 * as the create, together with the answer. A second request with the same key finds it and gets the saved answer. If the
 * create fails, the transaction rolls back and the key is forgotten, so a retry really runs again. Two requests at the
 * same moment cannot both win: the second waits on the database's unique index until the first finishes.
 *
 * <p>A key belongs to one person and one kind of request, and remembers a fingerprint of what was asked. Reusing a key
 * for a different request is refused instead of quietly returning the wrong record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentRequests {

	static final int MAX_KEY_LENGTH = 100;

	/** A key only has to outlive a retry, so it is kept for a day. */
	private static final Duration KEEP_FOR = Duration.ofHours(24);

	/** The status stored while the first request is still running. Never visible to another request (see class comment). */
	private static final int RUNNING = 0;

	private final JdbcClient jdbc;

	private final JsonMapper json;

	private final TransactionTemplate transactions;

	private final IdempotencyKeyRepository keys;

	/**
	 * Runs {@code action} once for this person, operation and key, and returns its result. Without a key the action simply
	 * runs. {@code request} is what the client asked for; it is only used to notice a key being reused for something else.
	 */
	public <T> T run(UUID userId, String operation, String key, Object request, Class<T> responseType, Supplier<T> action) {
		if (key == null || key.isBlank()) {
			return action.get();
		}
		if (key.length() > MAX_KEY_LENGTH) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency key is too long.");
		}
		String scope = operation + ":" + userId;
		String fingerprint = fingerprint(request);

		return this.transactions.execute((tx) -> {
			int claimed = this.jdbc.sql("""
					insert into idempotency_key (scope, key, response_status, request_hash) values (:scope, :key, :running, :hash)
					on conflict do nothing
					""")
				.param("scope", scope)
				.param("key", key)
				.param("running", RUNNING)
				.param("hash", fingerprint)
				.update();
			if (claimed == 0) {
				return replay(scope, key, fingerprint, responseType);
			}

			T result = action.get();
			this.jdbc.sql("""
					update idempotency_key set response_status = :status, response_body = cast(:body as jsonb)
					where scope = :scope and key = :key
					""")
				.param("status", 201)
				.param("body", this.json.writeValueAsString(result))
				.param("scope", scope)
				.param("key", key)
				.update();
			return result;
		});
	}

	private <T> T replay(String scope, String key, String fingerprint, Class<T> responseType) {
		record Saved(int status, String body, String hash) {
		}
		Saved saved = this.jdbc.sql("select response_status, response_body::text, request_hash from idempotency_key where scope = :scope and key = :key")
			.param("scope", scope)
			.param("key", key)
			.query((rs, i) -> new Saved(rs.getInt(1), rs.getString(2), rs.getString(3)))
			.single();
		if (saved.hash() != null && !saved.hash().equals(fingerprint)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED,
					"This request was already sent with different details. Reload the form and try again.");
		}
		if (saved.status() == RUNNING || saved.body() == null) {
			throw new ApiException(ErrorCode.INVALID_STATE, "This request is still being processed. Wait a moment and check the list.");
		}
		return this.json.readValue(saved.body(), responseType);
	}

	/** A short fingerprint of the request body: enough to tell two different requests apart, and nothing readable is stored. */
	private String fingerprint(Object request) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(this.json.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Forgets keys older than a day. */
	@Scheduled(cron = "0 20 3 * * *", zone = "UTC")
	public void purgeOld() {
		int removed = this.keys.deleteOlderThan(OffsetDateTime.now(ZoneOffset.UTC).minus(KEEP_FOR).toInstant());
		log.info("Idempotency keys cleaned up: {} removed", removed);
	}

}
