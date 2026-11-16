package com.nexus.crm.shared.currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A currency leads and deals can use. */
@Entity
@Table(name = "currency")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Currency {

	/** The ISO 4217 code, for example "INR". */
	@Id
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(length = 3)
	private String code;

	@Column(nullable = false)
	private String name;

	/** How many decimals the currency has: 2 for INR, 3 for KWD. */
	@Column(name = "minor_units", nullable = false)
	private short minorUnits;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "is_base", nullable = false)
	private boolean base;

	/** A new, disabled row for an ISO 4217 currency. Throws IllegalArgumentException if the code is not one. */
	public static Currency fromIso(String code) {
		java.util.Currency iso = java.util.Currency.getInstance(code);
		if (iso.getDefaultFractionDigits() < 0 || iso.getDefaultFractionDigits() > 4) {
			throw new IllegalArgumentException(code + " is not a currency that holds money.");
		}
		Currency currency = new Currency();
		currency.code = iso.getCurrencyCode();
		currency.name = iso.getDisplayName(java.util.Locale.ENGLISH);
		currency.minorUnits = (short) iso.getDefaultFractionDigits();
		return currency;
	}

	public void setEnabled(boolean enabled) {
		if (!enabled && this.base) {
			throw new IllegalStateException("The base currency cannot be disabled.");
		}
		this.enabled = enabled;
	}

}
