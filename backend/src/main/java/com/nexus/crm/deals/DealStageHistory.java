package com.nexus.crm.deals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One move of a deal between stages, with the amount at that time. Rows are only ever added. */
@Entity
@Table(name = "deal_stage_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DealStageHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "deal_id", nullable = false, updatable = false)
	private UUID dealId;

	/** Empty for the first entry, when the deal is created. */
	@Column(name = "from_stage", updatable = false)
	private String fromStage;

	@Column(name = "to_stage", nullable = false, updatable = false)
	private String toStage;

	@Column(name = "amount_at_change", nullable = false, updatable = false, precision = 19, scale = 4)
	private BigDecimal amountAtChange;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, updatable = false, length = 3)
	private String currency;

	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	@Column(name = "changed_by", updatable = false)
	private UUID changedBy;

	public DealStageHistory(Deal deal, String fromStage, UUID changedBy) {
		this.dealId = deal.getId();
		this.fromStage = fromStage;
		this.toStage = deal.getStageCode();
		this.amountAtChange = deal.getAmount();
		this.currency = deal.getCurrency();
		this.changedBy = changedBy;
		this.changedAt = Instant.now();
	}

}
