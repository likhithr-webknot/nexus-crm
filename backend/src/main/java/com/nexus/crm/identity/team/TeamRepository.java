package com.nexus.crm.identity.team;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

	boolean existsByName(String name);

	boolean existsByNameAndIdNot(String name, UUID id);

}
