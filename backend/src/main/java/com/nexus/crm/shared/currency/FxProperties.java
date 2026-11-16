package com.nexus.crm.shared.currency;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the daily exchange-rate download. */
@ConfigurationProperties("nexus.fx")
public record FxProperties(boolean enabled, String providerUrl) {
}
