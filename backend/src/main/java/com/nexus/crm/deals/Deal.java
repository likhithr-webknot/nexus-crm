package com.nexus.crm.deals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.nexus.crm.shared.persistence.BaseEntity;

/** A sale being worked on. When the deal is won or lost, the INR value on that day is saved with it, so reported revenue never changes afterwards. */
@Entity
@Table(name = "deal")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "company_id", nullable = false)
	private UUID companyId;

	@Column(name = "primary_contact_id")
	private UUID primaryContactId;

	/** The lead this deal was converted from, if any. */
	@Setter(AccessLevel.NONE)
	@Column(name = "lead_id", updatable = false)
	private UUID leadId;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	/** Changed only through {@link #moveTo}, {@link #win} and {@link #lose}. */
	@Setter(AccessLevel.NONE)
	@Column(name = "stage_code", nullable = false)
	private String stageCode;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal amount;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private String currency = "INR";

	/** Copied from the lead, so reports can show where deals come from. */
	private String source;

	@Column(name = "expected_close_date")
	private LocalDate expectedCloseDate;

	@Setter(AccessLevel.NONE)
	@Column(name = "closed_at")
	private Instant closedAt;

	@Setter(AccessLevel.NONE)
	@Column(name = "lost_reason")
	private String lostReason;

	/** The amount in INR on the day the deal closed. Empty while the deal is open. */
	@Setter(AccessLevel.NONE)
	@Column(name = "base_amount", precision = 19, scale = 4)
	private BigDecimal baseAmount;

	@Setter(AccessLevel.NONE)
	@Column(name = "fx_rate", precision = 18, scale = 8)
	private BigDecimal fxRate;

	@Setter(AccessLevel.NONE)
	@Column(name = "fx_rate_date")
	private LocalDate fxRateDate;

	public Deal(String name, UUID companyId, UUID ownerId, String stageCode, BigDecimal amount) {
		this.name = name;
		this.companyId = companyId;
		this.ownerId = ownerId;
		this.stageCode = stageCode;
		this.amount = amount;
	}

	public boolean isClosed() {
		return this.closedAt != null;
	}

	/** Moves the deal to another open stage. */
	public void moveTo(String openStageCode) {
		requireOpen();
		this.stageCode = openStageCode;
	}

	/** Marks the deal won and saves its INR value. */
	public void win(Instant at, BigDecimal baseAmount, BigDecimal fxRate, LocalDate fxRateDate) {
		close(PipelineStage.WON, at, baseAmount, fxRate, fxRateDate);
	}

	/** Marks the deal lost, with the reason, and saves its INR value. */
	public void lose(String reason, Instant at, BigDecimal baseAmount, BigDecimal fxRate, LocalDate fxRateDate) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reason is required to mark a deal lost.");
		}
		close(PipelineStage.LOST, at, baseAmount, fxRate, fxRateDate);
		this.lostReason = reason;
	}

	private void close(String stageCode, Instant at, BigDecimal baseAmount, BigDecimal fxRate, LocalDate fxRateDate) {
		requireOpen();
		this.stageCode = stageCode;
		this.closedAt = at;
		this.baseAmount = baseAmount;
		this.fxRate = fxRate;
		this.fxRateDate = fxRateDate;
	}

	private void requireOpen() {
		if (isClosed()) {
			throw new IllegalStateException("The deal is already closed.");
		}
	}

	/** Sets the lead this deal comes from. Used once, when the lead is converted. */
	public void convertedFrom(UUID leadId, String source) {
		this.leadId = leadId;
		this.source = source;
	}

}
