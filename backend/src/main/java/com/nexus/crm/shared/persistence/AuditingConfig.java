package com.nexus.crm.shared.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nexus.crm.shared.CurrentUser;

/** Turns on the automatic created/updated dates and users for {@link BaseEntity}. */
@Configuration
@EnableJpaAuditing
class AuditingConfig {

	/** The signed-in user's ID, or nothing when the change is not made by a signed-in user. */
	@Bean
	AuditorAware<UUID> auditorAware() {
		return () -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
				return Optional.of(user.id());
			}
			return Optional.empty();
		};
	}

}
