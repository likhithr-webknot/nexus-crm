package com.nexus.crm.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Who changed which record, and when. Rows are only ever added. */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

	public enum Action {

		CREATE, UPDATE, DELETE, RESTORE, CONVERT

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	/** Empty when the change was not made by a signed-in user. */
	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	/** The kind of record, for example "Lead". */
	@Column(name = "entity_type", nullable = false, updatable = false)
	private String entityType;

	@Column(name = "entity_id", nullable = false, updatable = false)
	private UUID entityId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Action action;

	/** JSON text: each changed field with its old and new value, like {"status": ["NEW", "CONTACTED"]}. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb", updatable = false)
	private String changes;

	/** The request ID from the logs, to find the request that made the change. */
	@Column(name = "request_id", updatable = false)
	private String requestId;

	public AuditLog(UUID actorId, String entityType, UUID entityId, Action action, String changes,
			String requestId) {
		this.occurredAt = Instant.now();
		this.actorId = actorId;
		this.entityType = entityType;
		this.entityId = entityId;
		this.action = action;
		this.changes = changes;
		this.requestId = requestId;
	}

}
