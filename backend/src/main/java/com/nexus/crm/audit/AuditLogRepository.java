package com.nexus.crm.audit;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, UUID entityId,
			Pageable page);

	Page<AuditLog> findByEntityTypeAndEntityId(String entityType, UUID entityId, Pageable page);

	Page<AuditLog> findByEntityType(String entityType, Pageable page);

}
