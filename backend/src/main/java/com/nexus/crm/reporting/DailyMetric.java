package com.nexus.crm.reporting;

import java.io.Serializable;
import java.math.BigDecimal;
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

/** One number for one user on one day, for example how many leads they created. */
@Entity
@Table(name = "daily_metric")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyMetric {

	@Embeddable
	public record Key(@Column(name = "day") LocalDate day, @Column(name = "owner_id") UUID ownerId,
			@Column(name = "metric") String metric) implements Serializable {
	}

	@EmbeddedId
	private Key id;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal value;

	public DailyMetric(LocalDate day, UUID ownerId, String metric, BigDecimal value) {
		this.id = new Key(day, ownerId, metric);
		this.value = value;
	}

}
