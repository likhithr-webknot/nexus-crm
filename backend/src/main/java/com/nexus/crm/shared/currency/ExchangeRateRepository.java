package com.nexus.crm.shared.currency;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, ExchangeRate.Key> {

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("delete from ExchangeRate r where r.id.currency = :currency")
	int deleteByIdCurrency(@Param("currency") String currency);

	/**
	 * Rates for a currency on or before a day, newest first. Pass {@code PageRequest.ofSize(1)} to get
	 * the rate to use for that day: the day's own rate, or the latest earlier one.
	 */
	@Query("""
			select r from ExchangeRate r
			where r.id.currency = :currency and r.id.rateDate <= :day
			order by r.id.rateDate desc
			""")
	List<ExchangeRate> findOnOrBefore(@Param("currency") String currency, @Param("day") LocalDate day,
			Pageable page);

}
