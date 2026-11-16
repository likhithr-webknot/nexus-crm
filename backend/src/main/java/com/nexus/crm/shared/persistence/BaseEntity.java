package com.nexus.crm.shared.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.nexus.crm.shared.error.ApiException;
import com.nexus.crm.shared.error.ErrorCode;

/** The columns every business table has: an ID, who created and changed the row and when, a version for optimistic locking, and a soft-delete time. The dates and users are filled in by Spring (see {@link AuditingConfig}), so subclasses never set them. */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

	@Id
	private UUID id = UUID.randomUUID();

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Empty when nobody is signed in, for example a lead from the website form. */
	@CreatedBy
	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	@LastModifiedBy
	@Column(name = "updated_by")
	private UUID updatedBy;

	/** Null until the row is first saved. Hibernate then keeps it up to date. */
	@Version
	private Integer version;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	/** Stops an edit made on an out-of-date copy. The client sends the version it last saw. */
	public void checkVersion(Integer expected) {
		if (expected != null && !expected.equals(this.version)) {
			throw new ApiException(ErrorCode.VERSION_CONFLICT, "The record was changed by someone else. Reload and retry.");
		}
	}

	public boolean isDeleted() {
		return this.deletedAt != null;
	}

	public void softDelete(Instant at) {
		this.deletedAt = at;
	}

	public void restore() {
		this.deletedAt = null;
	}

}
