package com.nexus.crm.contacts;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import com.nexus.crm.shared.persistence.BaseEntity;
import com.nexus.crm.shared.Emails;

/** A person at a company. */
@Entity
@Table(name = "contact")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contact extends BaseEntity {

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name", nullable = false)
	private String lastName;

	/** Saved in lower case. Unique among contacts that are not deleted. */
	@Setter(AccessLevel.NONE)
	@Column(columnDefinition = "citext")
	private String email;

	private String phone;

	@Column(name = "job_title")
	private String jobTitle;

	@Column(name = "company_id")
	private UUID companyId;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	public Contact(String firstName, String lastName, UUID ownerId) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.ownerId = ownerId;
	}

	public void setEmail(String email) {
		this.email = Emails.normalize(email);
	}

}
