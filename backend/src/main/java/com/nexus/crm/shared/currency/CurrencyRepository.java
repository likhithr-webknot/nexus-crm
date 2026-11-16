package com.nexus.crm.shared.currency;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyRepository extends JpaRepository<Currency, String> {

	List<Currency> findByEnabledTrue();

	Optional<Currency> findByBaseTrue();

}
