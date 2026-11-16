package com.nexus.crm.reporting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The revenue goal for one month. Always in INR, so there is no currency
 * field here: the database fills it in and only allows INR.
 */
@Entity
@Table(name = "revenue_target")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RevenueTarget {

	/** The first day of the month. */
	@Id
	private LocalDate month;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Any day in the month works. It is saved as the first day of that month. */
	public RevenueTarget(LocalDate dayInMonth, BigDecimal amount, UUID updatedBy) {
		this.month = dayInMonth.withDayOfMonth(1);
		change(amount, updatedBy);
	}

	public void change(BigDecimal amount, UUID updatedBy) {
		this.amount = amount;
		this.updatedBy = updatedBy;
		this.updatedAt = Instant.now();
	}

}
