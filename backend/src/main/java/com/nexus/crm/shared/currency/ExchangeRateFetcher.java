package com.nexus.crm.shared.currency;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.nexus.crm.shared.currency.dto.ProviderResponse;

/**
 * Downloads the daily exchange rates. It is a bean of its own so that saving the rates goes through
 * {@link CurrencyService}'s proxy: a call from a method to another method of the same class would skip
 * the transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeRateFetcher {

	private static final int ATTEMPTS = 3;

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final CurrencyService currencies;

	private final FxProperties properties;

	/** Named like the bean, because the scheduler is also an AsyncTaskExecutor and Spring picks by name. */
	private final AsyncTaskExecutor applicationTaskExecutor;

	/** Runs once a day. The download is outside any transaction, so a slow provider never holds a database connection. */
	@Scheduled(cron = "0 30 1 * * *", zone = "UTC")
	public void fetch() {
		if (!this.properties.enabled()) {
			return;
		}
		String url = this.properties.providerUrl().replace("{base}", this.currencies.base().getCode());
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
		requestFactory.setReadTimeout(TIMEOUT);
		RestClient client = RestClient.builder().requestFactory(requestFactory).build();

		for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
			try {
				ProviderResponse response = client.get().uri(url).retrieve().body(ProviderResponse.class);
				if (response == null || !"success".equals(response.result()) || response.rates() == null) {
					throw new IllegalStateException("The rate provider sent an unexpected answer.");
				}
				// One transaction for the whole day's rates: all are saved or none.
				int saved = this.currencies.storeProviderRates(LocalDate.now(ZoneOffset.UTC), response.rates(), "open.er-api.com");
				log.info("Exchange rates updated: {} saved", saved);
				return;
			}
			catch (RuntimeException ex) {
				log.warn("Fetching exchange rates failed (attempt {} of {}): {}", attempt, ATTEMPTS, ex.getMessage());
			}
		}
		log.error("Exchange rates could not be updated. The latest saved rates stay in use.");
	}

	/** Also once at startup, so a new install has rates straight away. Off the main thread, so a slow provider cannot delay startup. */
	@EventListener(ApplicationReadyEvent.class)
	void fetchAtStartup() {
		this.applicationTaskExecutor.execute(this::fetch);
	}

}
