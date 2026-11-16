package com.nexus.crm.audit;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import com.nexus.crm.audit.dto.AuditResponse;
import com.nexus.crm.identity.user.UserService;
import com.nexus.crm.shared.web.PageResponse;

/** Admins can read the audit log. */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
class AuditController {

	private final AuditService audit;

	private final UserService users;

	private final JsonMapper json;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	PageResponse<AuditResponse> list(@RequestParam(required = false) String entityType,
			@RequestParam(required = false) UUID entityId, @RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "25") int size) {
		Page<AuditLog> found = this.audit.find(entityType, entityId, page, PageResponse.clampSize(size));
		Set<UUID> actorIds = found.stream().map(AuditLog::getActorId).filter(java.util.Objects::nonNull)
			.collect(Collectors.toSet());
		Map<UUID, String> names = this.users.displayNames(actorIds);
		return PageResponse.of(found,
				(log) -> new AuditResponse(log.getId(), log.getOccurredAt(), log.getActorId(),
						names.get(log.getActorId()), log.getEntityType(), log.getEntityId(), log.getAction(),
						log.getChanges() == null ? null : this.json.readTree(log.getChanges())));
	}

}
