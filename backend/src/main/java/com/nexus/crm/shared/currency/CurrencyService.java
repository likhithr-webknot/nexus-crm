package com.nexus.crm.shared.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.crm.shared.currency.dto.Rate;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** Currencies and their daily exchange rates. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {

	public static final int MAX_RATE_AGE_DAYS = 7;

	private final CurrencyRepository currencies;

	private final ExchangeRateRepository rates;

	@Transactional(readOnly = true)
	public List<Currency> list() {
		return this.currencies.findAll(Sort.by("code"));
	}

	@Transactional(readOnly = true)
	public Currency base() {
		return this.currencies.findByBaseTrue().orElseThrow(() -> new IllegalStateException("No base currency."));
	}

	/** True if the code is a currency an admin has enabled. */
	@Transactional(readOnly = true)
	public boolean isEnabled(String code) {
		return this.currencies.findById(code).map(Currency::isEnabled).orElse(false);
	}

	/** Rejects an amount whose currency is not enabled, or that has more decimals than the currency allows. */
	@Transactional(readOnly = true)
	public void validate(Money money) {
		Currency currency = this.currencies.findById(money.currency().toUpperCase(java.util.Locale.ROOT))
			.filter(Currency::isEnabled)
			.orElseThrow(() -> new ApiException(ErrorCode.UNSUPPORTED_CURRENCY,
					money.currency() + " is not an enabled currency."));
		if (money.amount().stripTrailingZeros().scale() > currency.getMinorUnits()) {
			throw new ApiException(ErrorCode.INVALID_PRECISION,
					currency.getCode() + " allows at most " + currency.getMinorUnits() + " decimal places.");
		}
	}

	/** Enables or disables a currency. An ISO 4217 currency that is not in the table yet is added when enabled. */
	@Transactional
	public Currency setEnabled(String code, boolean enabled) {
		Currency currency = this.currencies.findById(code).orElse(null);
		if (currency == null) {
			if (!enabled) {
				throw new ApiException(ErrorCode.NOT_FOUND, "Currency not found.");
			}
			try {
				currency = this.currencies.save(Currency.fromIso(code));
			}
			catch (IllegalArgumentException ex) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, code + " is not a currency we can use.");
			}
		}
		try {
			currency.setEnabled(enabled);
		}
		catch (IllegalStateException ex) {
			throw new ApiException(ErrorCode.INVALID_STATE, ex.getMessage());
		}
		return currency;
	}

	/** The rate to use for a currency on a day, or empty if there has never been a rate before that day. */
	@Transactional(readOnly = true)
	public Optional<Rate> rateOn(String currency, LocalDate day) {
		if (currency.equals(base().getCode())) {
			return Optional.of(new Rate(currency, BigDecimal.ONE, day, "BASE", false));
		}
		return this.rates.findOnOrBefore(currency, day, PageRequest.ofSize(1)).stream().findFirst().map((rate) -> {
			LocalDate rateDate = rate.getId().rateDate();
			boolean stale = rateDate.isBefore(day.minusDays(MAX_RATE_AGE_DAYS));
			return new Rate(currency, rate.getRateToBase(), rateDate, rate.getSource(), stale);
		});
	}

	/** The latest rate of every enabled currency except the base, for the frontend's conversions. */
	@Transactional(readOnly = true)
	public List<Rate> latestRates() {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		return this.currencies.findByEnabledTrue()
			.stream()
			.filter((currency) -> !currency.isBase())
			.flatMap((currency) -> rateOn(currency.getCode(), today).stream())
			.toList();
	}

	/** Removes a currency nobody uses. One that leads or deals still refer to has to be disabled instead. */
	@Transactional
	public void deleteCurrency(String code) {
		Currency currency = this.currencies.findById(code)
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Currency not found."));
		if (currency.isBase()) {
			throw new ApiException(ErrorCode.INVALID_STATE, "The base currency cannot be deleted.");
		}
		try {
			this.rates.deleteByIdCurrency(code);
			this.currencies.delete(currency);
			this.currencies.flush();
		}
		catch (org.springframework.dao.DataIntegrityViolationException ex) {
			throw new ApiException(ErrorCode.INVALID_STATE, code + " is used by leads or deals. Disable it instead.");
		}
	}

	/** Takes back a rate an admin typed in. The next download fills the day in again. Downloaded rates are kept. */
	@Transactional
	public void deleteOverride(String currency, LocalDate day) {
		ExchangeRate rate = this.rates.findById(new ExchangeRate.Key(currency, day))
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "There is no rate for that day."));
		if (!ExchangeRate.MANUAL.equals(rate.getSource())) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Only a rate set by hand can be removed.");
		}
		this.rates.delete(rate);
	}

	/** An admin types in the rate for a day, for example to match the finance team's monthly rate. */
	@Transactional
	public Rate override(String currency, LocalDate day, BigDecimal rateToBase, UUID adminId) {
		Currency known = this.currencies.findById(currency)
			.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Currency not found."));
		if (known.isBase()) {
			throw new ApiException(ErrorCode.INVALID_STATE, "The base currency always has a rate of 1.");
		}
		ExchangeRate rate = this.rates.findById(new ExchangeRate.Key(currency, day)).orElse(null);
		if (rate == null) {
			rate = new ExchangeRate(currency, day, rateToBase, ExchangeRate.MANUAL);
		}
		rate.overrideBy(adminId, rateToBase);
		this.rates.save(rate);
		log.info("Exchange rate set by hand: currency={} day={} rate={} by={}", currency, day, rateToBase, adminId);
		return new Rate(currency, rateToBase, day, ExchangeRate.MANUAL, false);
	}

	/** Saves a day's rates from the provider. */
	/** Called by {@link ExchangeRateFetcher}, which is another bean, so this transaction really applies. */
	@Transactional
	public int storeProviderRates(LocalDate day, Map<String, BigDecimal> unitsPerBase, String source) {
		int saved = 0;
		for (Currency currency : this.currencies.findByEnabledTrue()) {
			BigDecimal units = unitsPerBase.get(currency.getCode());
			if (currency.isBase() || units == null || units.signum() <= 0) {
				continue;
			}
			ExchangeRate.Key key = new ExchangeRate.Key(currency.getCode(), day);
			if (this.rates.findById(key).filter((rate) -> ExchangeRate.MANUAL.equals(rate.getSource())).isPresent()) {
				continue;
			}
			BigDecimal rateToBase = BigDecimal.ONE.divide(units, 8, RoundingMode.HALF_UP);
			this.rates.save(new ExchangeRate(currency.getCode(), day, rateToBase, source));
			saved++;
		}
		return saved;
	}

}
