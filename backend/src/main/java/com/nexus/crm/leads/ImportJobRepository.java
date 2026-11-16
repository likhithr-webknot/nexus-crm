package com.nexus.crm.leads;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

}
