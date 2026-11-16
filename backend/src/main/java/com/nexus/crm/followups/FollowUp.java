package com.nexus.crm.followups;

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

/** A task with a due date, given to one user. It belongs to at least one lead, contact or deal. */
@Entity
@Table(name = "follow_up")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowUp extends BaseEntity {

	public enum Type {

		CALL, MEETING, EMAIL, TASK

	}

	public enum Status {

		OPEN, DONE, CANCELLED

	}

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Type type = Type.TASK;

	@Column(nullable = false)
	private String title;

	private String notes;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@Column(name = "assignee_id", nullable = false)
	private UUID assigneeId;

	@Setter(AccessLevel.NONE)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Setter(AccessLevel.NONE)
	@Column(name = "completed_at")
	private Instant completedAt;

	/** When the in-app reminder was sent. Empty until then, so the reminder job sends it only once. */
	@Setter(AccessLevel.NONE)
	@Column(name = "reminded_at")
	private Instant remindedAt;

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
	public FollowUp(String title, Type type, Instant dueAt, UUID assigneeId, UUID leadId, UUID contactId, UUID dealId) {
		if (leadId == null && contactId == null && dealId == null) {
			throw new IllegalArgumentException("A follow-up needs a lead, a contact or a deal.");
		}
		this.title = title;
		this.type = type;
		this.dueAt = dueAt;
		this.assigneeId = assigneeId;
		this.leadId = leadId;
		this.contactId = contactId;
		this.dealId = dealId;
		this.status = Status.OPEN;
	}

	public void complete(Instant at) {
		requireOpen();
		this.status = Status.DONE;
		this.completedAt = at;
	}

	/** Puts a finished follow-up back on the list, for example when it was ticked by mistake. */
	public void reopen() {
		if (this.status == Status.DONE) {
			this.status = Status.OPEN;
			this.completedAt = null;
		}
	}

	public void cancel() {
		requireOpen();
		this.status = Status.CANCELLED;
	}

	public void markReminded(Instant at) {
		this.remindedAt = at;
	}

	private void requireOpen() {
		if (this.status != Status.OPEN) {
			throw new IllegalStateException("The follow-up is already " + this.status + ".");
		}
	}

}
