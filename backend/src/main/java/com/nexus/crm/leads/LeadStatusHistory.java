package com.nexus.crm.leads;

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

/** One change of a lead's status. Rows are only ever added. */
@Entity
@Table(name = "lead_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeadStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "lead_id", nullable = false, updatable = false)
	private UUID leadId;

	/** Empty for the first entry, when the lead is created. */
	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", updatable = false)
	private Lead.Status fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, updatable = false)
	private Lead.Status toStatus;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	@Column(name = "changed_by", updatable = false)
	private UUID changedBy;

	public LeadStatusHistory(UUID leadId, Lead.Status fromStatus, Lead.Status toStatus, UUID changedBy) {
		this.leadId = leadId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.changedBy = changedBy;
		this.changedAt = Instant.now();
	}

}
