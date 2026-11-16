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

/** A company that contacts work for and deals are sold to. */
@Entity
@Table(name = "company")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

	@Column(nullable = false)
	private String name;

	/** The website domain in lower case, for example "apexdynamics.io". Unique among companies that are not deleted. */
	@Setter(AccessLevel.NONE)
	@Column(columnDefinition = "citext")
	private String domain;

	private String industry;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	public Company(String name, UUID ownerId) {
		this.name = name;
		this.ownerId = ownerId;
	}

	public void setDomain(String domain) {
		this.domain = Emails.normalize(domain);
	}

}
