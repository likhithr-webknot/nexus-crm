package com.nexus.crm.leads;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.nexus.crm.shared.persistence.BaseEntity;
import com.nexus.crm.shared.Emails;

/**
 * Someone who might become a customer. A lead moves NEW, CONTACTED, QUALIFIED,
 * and then ends as CONVERTED (a deal was created) or DISQUALIFIED (with a reason).
 *
 * <p>
 * The database also has a {@code search_text} column that it fills in itself.
 * It is not mapped here because only search queries use it.
 */
@Entity
@Table(name = "lead")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lead extends BaseEntity {

	public enum Source {

		WEBSITE, SOCIAL, REFERRAL, CAMPAIGN, MANUAL, IMPORT

	}

	public enum Status {

		NEW, CONTACTED, QUALIFIED, DISQUALIFIED, CONVERTED

	}

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name", nullable = false)
	private String lastName;

	/** Saved in lower case. Only one open lead may have a given email. */
	@Setter(AccessLevel.NONE)
	@Column(columnDefinition = "citext")
	private String email;

	private String phone;

	@Column(name = "company_name")
	private String companyName;

	@Column(name = "job_title")
	private String jobTitle;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Source source;

	/** Changed only through the methods below, which keep the database's rules. */
	@Setter(AccessLevel.NONE)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status;

	/** Empty while the lead is unassigned. */
	@Column(name = "owner_id")
	private UUID ownerId;

	@Column(name = "estimated_value", precision = 19, scale = 4)
	private BigDecimal estimatedValue;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(nullable = false, length = 3)
	private String currency = "INR";

	@Setter(AccessLevel.NONE)
	@Column(name = "disqualify_reason")
	private String disqualifyReason;

	@Setter(AccessLevel.NONE)
	@Column(name = "converted_at")
	private Instant convertedAt;

	@Setter(AccessLevel.NONE)
	@Column(name = "converted_company_id")
	private UUID convertedCompanyId;

	@Setter(AccessLevel.NONE)
	@Column(name = "converted_contact_id")
	private UUID convertedContactId;

	@Setter(AccessLevel.NONE)
	@Column(name = "converted_deal_id")
	private UUID convertedDealId;

	public Lead(String firstName, String lastName, Source source) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.source = source;
		this.status = Status.NEW;
	}

	public void setEmail(String email) {
		this.email = Emails.normalize(email);
	}

	/** Moves an open lead to NEW, CONTACTED or QUALIFIED. Disqualifying and converting have their own methods. */
	public void changeStatus(Status newStatus) {
		if (newStatus == Status.DISQUALIFIED || newStatus == Status.CONVERTED) {
			throw new IllegalArgumentException("Use disqualify() or convert() for " + newStatus);
		}
		requireOpen();
		this.status = newStatus;
	}

	public void disqualify(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reason is required to disqualify a lead.");
		}
		requireOpen();
		this.status = Status.DISQUALIFIED;
		this.disqualifyReason = reason;
	}

	public void convert(UUID companyId, UUID contactId, UUID dealId, Instant at) {
		requireOpen();
		this.status = Status.CONVERTED;
		this.convertedCompanyId = companyId;
		this.convertedContactId = contactId;
		this.convertedDealId = dealId;
		this.convertedAt = at;
	}

	private void requireOpen() {
		if (this.status == Status.DISQUALIFIED || this.status == Status.CONVERTED) {
			throw new IllegalStateException("The lead is already " + this.status + ".");
		}
	}

}
