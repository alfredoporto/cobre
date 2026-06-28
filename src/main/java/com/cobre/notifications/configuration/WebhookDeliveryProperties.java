package com.cobre.notifications.configuration;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cobre.webhook")
public record WebhookDeliveryProperties(
        URI url,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxAttempts,
        Duration retryBackoff) {
}
