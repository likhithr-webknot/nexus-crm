package com.nexus.crm.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/** Pass the email through {@link AppUser#normalizeEmail} first. */
	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

}
