package com.nexus.crm.reporting;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DailyMetricRepository extends JpaRepository<DailyMetric, DailyMetric.Key> {

	/** The nightly job clears a day before writing it again. */
	@Modifying
	@Transactional
	@Query("delete from DailyMetric m where m.id.day = :day")
	int deleteByDay(@Param("day") LocalDate day);

}
