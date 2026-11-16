package com.nexus.crm.shared.currency;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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

/** The rate of one currency on one day, in the base currency (INR). */
@Entity
@Table(name = "exchange_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRate {

	/** Where an admin's manual rate comes from. */
	public static final String MANUAL = "MANUAL";

	@Embeddable
	public record Key(@JdbcTypeCode(SqlTypes.CHAR) @Column(name = "currency", length = 3) String currency,
			@Column(name = "rate_date") LocalDate rateDate) implements Serializable {
	}

	@EmbeddedId
	private Key id;

	/** One unit of the currency is worth this many units of the base currency. */
	@Column(name = "rate_to_base", nullable = false, precision = 18, scale = 8)
	private BigDecimal rateToBase;

	/** The provider's name, or {@link #MANUAL}. */
	@Column(nullable = false)
	private String source;

	/** Set only for a manual rate. */
	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** A rate fetched from a provider. */
	public ExchangeRate(String currency, LocalDate rateDate, BigDecimal rateToBase, String source) {
		this.id = new Key(currency, rateDate);
		this.rateToBase = rateToBase;
		this.source = source;
		this.updatedAt = Instant.now();
	}

	/** Replaces the rate with one an admin typed in. */
	public void overrideBy(UUID adminId, BigDecimal rateToBase) {
		this.rateToBase = rateToBase;
		this.source = MANUAL;
		this.updatedBy = adminId;
		this.updatedAt = Instant.now();
	}

}
