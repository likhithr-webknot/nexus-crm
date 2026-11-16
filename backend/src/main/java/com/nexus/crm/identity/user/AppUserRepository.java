package com.nexus.crm.identity.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexus.crm.shared.Role;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/** Pass the email through {@link AppUser#normalizeEmail} first. */
	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

	/** The IDs of everyone in a team that this manager leads. */
	@Query("select u.id from AppUser u where u.teamId in (select t.id from Team t where t.managerId = :managerId)")
	List<UUID> findIdsInTeamsLedBy(@Param("managerId") UUID managerId);

	long countByTeamId(UUID teamId);

	List<AppUser> findByRoleAndStatus(Role role, AppUser.Status status);

	List<AppUser> findByIdIn(Collection<UUID> ids);

}
