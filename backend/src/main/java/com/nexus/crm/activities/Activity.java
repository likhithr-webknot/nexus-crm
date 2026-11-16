package com.nexus.crm.activities;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import com.nexus.crm.shared.persistence.BaseEntity;

/** An entry on the timeline of a lead, contact or deal: a note, call or meeting written by a person, or a SYSTEM entry the app writes itself (for example "Stage changed"). */
@Entity
@Table(name = "activity")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity extends BaseEntity {

	public enum Type {

		NOTE, CALL, MEETING, EMAIL_LOG, SYSTEM

	}

	@Setter(AccessLevel.NONE)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Type type;

	@Column(nullable = false)
	private String subject;

	/** Plain text. The frontend shows it escaped. */
	private String body;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	/** The person who wrote it. Empty for SYSTEM entries. */
	@Setter(AccessLevel.NONE)
	@Column(name = "actor_id", updatable = false)
	private UUID actorId;

	@Setter(AccessLevel.NONE)
	@Column(name = "lead_id", updatable = false)
	private UUID leadId;

	@Setter(AccessLevel.NONE)
	@Column(name = "contact_id", updatable = false)
	private UUID contactId;

	@Setter(AccessLevel.NONE)
	@Column(name = "deal_id", updatable = false)
	private UUID dealId;

	/** At least one of leadId, contactId and dealId must be given. */
	public Activity(Type type, String subject, Instant occurredAt, UUID actorId, UUID leadId, UUID contactId,
			UUID dealId) {
		if (leadId == null && contactId == null && dealId == null) {
			throw new IllegalArgumentException("An activity needs a lead, a contact or a deal.");
		}
		this.type = type;
		this.subject = subject;
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.leadId = leadId;
		this.contactId = contactId;
		this.dealId = dealId;
	}

}
