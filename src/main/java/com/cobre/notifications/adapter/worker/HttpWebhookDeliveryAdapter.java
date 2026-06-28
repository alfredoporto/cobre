package com.cobre.notifications.adapter.worker;

import com.cobre.notifications.application.port.WebhookDeliveryPort;
import com.cobre.notifications.application.port.WebhookDeliveryResult;
import com.cobre.notifications.configuration.WebhookDeliveryProperties;
import com.cobre.notifications.domain.DeliveryResult;
import com.cobre.notifications.domain.NotificationEvent;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class HttpWebhookDeliveryAdapter implements WebhookDeliveryPort {

    private final WebhookDeliveryProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpWebhookDeliveryAdapter(
            WebhookDeliveryProperties properties,
            HttpClient httpClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<WebhookDeliveryResult> send(NotificationEvent notificationEvent, int attemptNumber, String correlationId) {
        int maxAttempts = Math.max(properties.maxAttempts(), 1);
        List<WebhookDeliveryResult> results = new ArrayList<>();
        for (int deliveryAttempt = 0; deliveryAttempt < maxAttempts; deliveryAttempt++) {
            int currentAttemptNumber = attemptNumber + deliveryAttempt;
            WebhookDeliveryResult lastResult = sendOnce(notificationEvent, currentAttemptNumber, correlationId);
            results.add(lastResult);
            if (lastResult.successful() || !lastResult.retryable()) {
                return results;
            }
            sleepBeforeRetry();
        }
        return results;
    }

    private WebhookDeliveryResult sendOnce(
            NotificationEvent notificationEvent,
            int attemptNumber,
            String correlationId) {
        Instant startedAt = Instant.now();
        try {
            String body = bodyFor(notificationEvent);
            HttpRequest request = HttpRequest.newBuilder(properties.url())
                    .timeout(properties.requestTimeout())
                    .header("Content-Type", "application/json")
                    .header("X-Cobre-Notification-Id", notificationEvent.notificationEventId())
                    .header("X-Cobre-Event-Id", notificationEvent.sourceEventId())
                    .header("X-Cobre-Delivery-Attempt", Integer.toString(attemptNumber))
                    .header("X-Cobre-Timestamp", Instant.now().toString())
                    .header("X-Correlation-Id", correlationId)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            return classifyHttpStatus(response.statusCode(), latencyMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            return new WebhookDeliveryResult(
                    DeliveryResult.FAILED,
                    null,
                    latencyMs,
                    "WEBHOOK_INTERRUPTED",
                    "Webhook delivery was interrupted",
                    true);
        } catch (IOException | JacksonException exception) {
            long latencyMs = Duration.between(startedAt, Instant.now()).toMillis();
            return new WebhookDeliveryResult(
                    DeliveryResult.FAILED,
                    null,
                    latencyMs,
                    "WEBHOOK_CONNECTIVITY_ERROR",
                    "Webhook endpoint could not be reached",
                    true);
        }
    }

    private WebhookDeliveryResult classifyHttpStatus(int statusCode, long latencyMs) {
        if (statusCode >= 200 && statusCode < 300) {
            return new WebhookDeliveryResult(DeliveryResult.COMPLETED, statusCode, latencyMs, null, null, false);
        }
        if (statusCode == 429 || statusCode >= 500) {
            return new WebhookDeliveryResult(
                    DeliveryResult.FAILED,
                    statusCode,
                    latencyMs,
                    "HTTP_" + statusCode,
                    "Webhook endpoint returned a transient error",
                    true);
        }
        return new WebhookDeliveryResult(
                DeliveryResult.FAILED,
                statusCode,
                latencyMs,
                "HTTP_" + statusCode,
                "Webhook endpoint returned a permanent error",
                false);
    }

    private String bodyFor(NotificationEvent notificationEvent) throws JacksonException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("notification_event_id", notificationEvent.notificationEventId());
        body.put("event_type", notificationEvent.eventType());
        body.put("client_id", notificationEvent.clientId());
        body.put("created_at", notificationEvent.createdAt().toString());
        body.put("payload", notificationEvent.payload());
        return objectMapper.writeValueAsString(body);
    }

    private void sleepBeforeRetry() {
        Duration backoff = properties.retryBackoff();
        if (backoff.isZero() || backoff.isNegative()) {
            return;
        }
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
