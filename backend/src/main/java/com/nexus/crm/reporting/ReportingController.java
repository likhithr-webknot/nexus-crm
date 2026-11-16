package com.nexus.crm.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ErrorCode;

/** Everything here is limited to what the signed-in user may see. Money is in INR. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class ReportingController {

	/** The longest date range one request may ask for. */
	private static final int MAX_RANGE_DAYS = 366;

	record TargetResponse(String month, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {
	}

	record TargetRequest(@NotNull @DecimalMin("0") BigDecimal amount) {
	}

	record MonthResponse(String month, double revenue, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal target) {
	}

	record DashboardResponse(LocalDate from, LocalDate to, ReportingService.Dashboard data) {
	}

	private final ReportingService reporting;

	/** {@code from} and {@code to} are days, both included. They default to this month. */
	@GetMapping("/dashboard/summary")
	DashboardResponse dashboard(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
		LocalDate[] range = range(from, to);
		return new DashboardResponse(range[0], range[1], this.reporting.dashboard(user, range[0], range[1]));
	}

	@GetMapping("/reports/overview")
	ReportingService.Overview overview(@AuthenticationPrincipal CurrentUser user,
			@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
		LocalDate[] range = range(from, to);
		return this.reporting.overview(user, range[0], range[1]);
	}

	@GetMapping("/reports/revenue")
	List<MonthResponse> revenue(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "6") int months) {
		if (months < 1 || months > 24) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "months must be between 1 and 24.");
		}
		return this.reporting.revenueByMonth(user, months)
			.stream()
			.map((m) -> new MonthResponse(m.month().toString(), m.revenue(), m.target()))
			.toList();
	}

	@GetMapping("/revenue-targets")
	@PreAuthorize("hasRole('ADMIN')")
	List<TargetResponse> targets(@RequestParam(required = false) Integer year) {
		int y = (year != null) ? year : LocalDate.now(ZoneOffset.UTC).getYear();
		return this.reporting.targetsForYear(y).stream()
			.map((t) -> new TargetResponse(YearMonth.from(t.getMonth()).toString(), t.getAmount()))
			.toList();
	}

	@PutMapping("/revenue-targets/{month}")
	@PreAuthorize("hasRole('ADMIN')")
	TargetResponse setTarget(@PathVariable String month, @Valid @RequestBody TargetRequest request,
			@AuthenticationPrincipal CurrentUser admin) {
		YearMonth parsed;
		try {
			parsed = YearMonth.parse(month);
		}
		catch (java.time.format.DateTimeParseException ex) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "The month must look like 2026-11.");
		}
		RevenueTarget target = this.reporting.setTarget(parsed, request.amount(), admin.id());
		return new TargetResponse(parsed.toString(), target.getAmount());
	}

	private static LocalDate[] range(LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		LocalDate start = (from != null) ? from : today.withDayOfMonth(1);
		LocalDate end = (to != null) ? to : today.withDayOfMonth(today.lengthOfMonth());
		if (end.isBefore(start) || start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a range of at most a year, with the start before the end.");
		}
		return new LocalDate[] { start, end };
	}

}
