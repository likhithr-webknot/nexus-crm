package com.nexus.crm.shared.currency;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.currency.dto.CurrencyResponse;
import com.nexus.crm.shared.currency.dto.RateRequest;
import com.nexus.crm.shared.currency.dto.RateResponse;
import com.nexus.crm.shared.currency.dto.UpdateCurrencyRequest;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** Anyone signed in can read currencies and rates. Only admins can change them. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class CurrencyController {

	private final CurrencyService currencies;

	@GetMapping("/currencies")
	List<CurrencyResponse> list() {
		return this.currencies.list().stream().map(CurrencyResponse::from).toList();
	}

	@PatchMapping("/currencies/{code}")
	@PreAuthorize("hasRole('ADMIN')")
	CurrencyResponse update(@PathVariable String code, @Valid @RequestBody UpdateCurrencyRequest request) {
		return CurrencyResponse.from(this.currencies.setEnabled(code(code), request.enabled()));
	}

	@DeleteMapping("/currencies/{code}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable String code) {
		this.currencies.deleteCurrency(code(code));
	}

	@GetMapping("/exchange-rates/latest")
	List<RateResponse> latest() {
		return this.currencies.latestRates().stream().map(RateResponse::from).toList();
	}

	@PutMapping("/exchange-rates/{currency}/{date}")
	@PreAuthorize("hasRole('ADMIN')")
	RateResponse override(@PathVariable String currency, @PathVariable LocalDate date,
			@Valid @RequestBody RateRequest request, @AuthenticationPrincipal CurrentUser admin) {
		UUID adminId = admin.id();
		return RateResponse.from(this.currencies.override(code(currency), date, request.rateToBase(), adminId));
	}

	@DeleteMapping("/exchange-rates/{currency}/{date}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deleteOverride(@PathVariable String currency, @PathVariable LocalDate date) {
		this.currencies.deleteOverride(code(currency), date);
	}

	private static String code(String value) {
		if (value == null || !value.matches("[A-Za-z]{3}")) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "A currency code has three letters.");
		}
		return value.toUpperCase(java.util.Locale.ROOT);
	}

}
