package com.nexus.crm.leads;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A CSV import of leads. The upload returns at once and the import runs in the background. */
@Entity
@Table(name = "import_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImportJob {

	public enum Status {

		QUEUED, RUNNING, DONE, FAILED

	}

	@Id
	private UUID id;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	@Column(name = "total_rows")
	private Integer totalRows;

	@Column(name = "imported_rows")
	private Integer importedRows;

	@Column(name = "failed_rows")
	private Integer failedRows;

	/** JSON text: the row number and reason for each row that failed. The user can download it. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "error_report", columnDefinition = "jsonb")
	private String errorReport;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	public ImportJob(UUID uploadedBy) {
		this.id = UUID.randomUUID();
		this.uploadedBy = uploadedBy;
		this.status = Status.QUEUED;
		this.createdAt = Instant.now();
	}

	public void start() {
		this.status = Status.RUNNING;
	}

	public void finish(int totalRows, int importedRows, int failedRows, String errorReport) {
		this.status = Status.DONE;
		this.totalRows = totalRows;
		this.importedRows = importedRows;
		this.failedRows = failedRows;
		this.errorReport = errorReport;
		this.finishedAt = Instant.now();
	}

	/** The whole file could not be read, so no row was imported. */
	public void fail(String errorReport) {
		this.status = Status.FAILED;
		this.errorReport = errorReport;
		this.finishedAt = Instant.now();
	}

}
