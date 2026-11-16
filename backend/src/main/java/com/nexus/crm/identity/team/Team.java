package com.nexus.crm.identity.team;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A group of reps led by a manager. A user belongs to one team at most ({@code AppUser.teamId}). */
@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team {

	@Setter(AccessLevel.NONE)
	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	/** Empty while the team has no manager. */
	@Column(name = "manager_id")
	private UUID managerId;

	public Team(String name, UUID managerId) {
		this.id = UUID.randomUUID();
		this.name = name;
		this.managerId = managerId;
	}

}
