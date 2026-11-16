package com.nexus.crm.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UserIdentityRepository extends JpaRepository<UserIdentity, UUID> {

	Optional<UserIdentity> findByProviderAndSubject(String provider, String subject);

	boolean existsByUserIdAndProvider(UUID userId, String provider);

}
