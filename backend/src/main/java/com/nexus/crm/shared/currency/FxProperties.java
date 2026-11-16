package com.nexus.crm.shared.currency;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the daily exchange-rate download.
 *
 * @param enabled turn the download off, for example in tests
 * @param providerUrl the rate provider. {@code {base}} is replaced by the base currency code.
 */
@ConfigurationProperties("nexus.fx")
public record FxProperties(boolean enabled, String providerUrl) {
}
