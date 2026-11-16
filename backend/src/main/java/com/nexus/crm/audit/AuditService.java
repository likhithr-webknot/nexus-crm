package com.nexus.crm.audit;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Writes the audit log. */
@Service
@RequiredArgsConstructor
public class AuditService {

	/** The fields that changed, each with its old and new value. Fields that did not change are left out. */
	public static final class Changes {

		private final Map<String, List<Object>> fields = new LinkedHashMap<>();

		public Changes field(String name, Object oldValue, Object newValue) {
			if (!Objects.equals(oldValue, newValue)) {
				this.fields.put(name, Arrays.asList(oldValue, newValue));
			}
			return this;
		}

		public boolean isEmpty() {
			return this.fields.isEmpty();
		}

	}

	public static Changes changes() {
		return new Changes();
	}

	private final AuditLogRepository logs;

	private final JsonMapper json;

	/** Records one change. Pass null for {@code changes} when there is nothing to list, for example a new record. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(UUID actorId, String entityType, UUID entityId, AuditLog.Action action, Changes changes) {
		String changesJson = (changes == null || changes.isEmpty()) ? null : this.json.writeValueAsString(changes.fields);
		this.logs.save(new AuditLog(actorId, entityType, entityId, action, changesJson, MDC.get("requestId")));
	}

	@Transactional(readOnly = true)
	public org.springframework.data.domain.Page<AuditLog> find(String entityType, UUID entityId, int page, int size) {
		PageRequest paging = PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
		if (entityType != null && entityId != null) {
			return this.logs.findByEntityTypeAndEntityId(entityType, entityId, paging);
		}
		if (entityType != null) {
			return this.logs.findByEntityType(entityType, paging);
		}
		return this.logs.findAll(paging);
	}

}
