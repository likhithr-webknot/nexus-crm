package com.nexus.crm.identity.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	/** Finds a token and locks it, so two requests using the same token are handled one at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from RefreshToken t where t.tokenHash = :hash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") byte[] hash);

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
	int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
	int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

}
