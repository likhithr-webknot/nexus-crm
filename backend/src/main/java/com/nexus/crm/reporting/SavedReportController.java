package com.nexus.crm.reporting;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexus.crm.audit.AuditLog;
import com.nexus.crm.audit.AuditService;
import com.nexus.crm.shared.CurrentUser;
import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

@RestController
@RequestMapping("/api/v1/saved-reports")
@RequiredArgsConstructor
class SavedReportController {
	private final JdbcClient jdbc;
	private final AuditService audit;
	private static final Set<String> DATASETS = Set.of("revenue", "team", "funnel", "sources");
	private static final Set<String> CHARTS = Set.of("bar", "line", "pie");
	record Report(UUID id, String name, String dataset, String chart, Instant createdAt) { }
	record Input(String name, String dataset, String chart) { }

	@GetMapping
	List<Report> list(@AuthenticationPrincipal CurrentUser user) {
		return jdbc.sql("select id,name,dataset,chart,created_at from saved_report where owner_id=:user order by created_at desc limit 100")
			.param("user", user.id()).query((rs, row) -> new Report(rs.getObject("id", UUID.class), rs.getString("name"),
				rs.getString("dataset"), rs.getString("chart"), rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant())).list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	Report create(@AuthenticationPrincipal CurrentUser user, @RequestBody Input input) {
		if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 80
				|| !DATASETS.contains(input.dataset()) || !CHARTS.contains(input.chart()))
			throw new ApiException(ErrorCode.VALIDATION_FAILED, "Choose a report name, dataset and chart.");
		UUID id = UUID.randomUUID();
		jdbc.sql("insert into saved_report (id,owner_id,name,dataset,chart) values (:id,:user,:name,:dataset,:chart)")
			.param("id", id).param("user", user.id()).param("name", input.name().strip())
			.param("dataset", input.dataset()).param("chart", input.chart()).update();
		audit.record(user.id(), "SavedReport", id, AuditLog.Action.CREATE, null);
		return new Report(id, input.name().strip(), input.dataset(), input.chart(), Instant.now());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void remove(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		int count = jdbc.sql("delete from saved_report where id=:id and owner_id=:user")
			.param("id", id).param("user", user.id()).update();
		if (count == 0) throw new ApiException(ErrorCode.NOT_FOUND, "Saved report not found.");
		audit.record(user.id(), "SavedReport", id, AuditLog.Action.DELETE, null);
	}
}
