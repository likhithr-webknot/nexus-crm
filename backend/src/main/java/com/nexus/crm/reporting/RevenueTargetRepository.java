package com.nexus.crm.reporting;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RevenueTargetRepository extends JpaRepository<RevenueTarget, LocalDate> {

}
