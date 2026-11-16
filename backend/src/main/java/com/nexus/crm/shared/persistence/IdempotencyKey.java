package com.nexus.crm.shared.persistence;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The saved answer to a request that came with an {@code Idempotency-Key} header. */
@Entity
@Table(name = "idempotency_key")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

	/** The endpoint name plus the key the client sent. */
	@Embeddable
	public record Key(@Column(name = "scope") String scope, @Column(name = "key") String key) implements Serializable {
	}

	@EmbeddedId
	private Key id;

	@Column(name = "response_status", nullable = false, updatable = false)
	private int responseStatus;

	/** The response as JSON text. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response_body", columnDefinition = "jsonb", updatable = false)
	private String responseBody;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IdempotencyKey(String scope, String key, int responseStatus, String responseBody) {
		this.id = new Key(scope, key);
		this.responseStatus = responseStatus;
		this.responseBody = responseBody;
		this.createdAt = Instant.now();
	}

}
