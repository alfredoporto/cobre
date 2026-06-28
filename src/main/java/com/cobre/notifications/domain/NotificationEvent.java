package com.cobre.notifications.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class NotificationEvent {

    private final String notificationEventId;
    private final String sourceEventId;
    private final String clientId;
    private final String eventType;
    private final String contentSummary;
    private final Map<String, Object> payload;
    private final Instant createdAt;
    private DeliveryStatus deliveryStatus;
    private Instant lastAttemptAt;
    private Instant nextRetryAt;
    private String finalErrorCode;
    private String finalErrorMessage;
    private final List<DeliveryAttempt> deliveryAttempts;

    public NotificationEvent(
            String notificationEventId,
            String sourceEventId,
            String clientId,
            String eventType,
            String contentSummary,
            Map<String, Object> payload,
            Instant createdAt,
            DeliveryStatus deliveryStatus,
            List<DeliveryAttempt> deliveryAttempts,
            String finalErrorCode,
            String finalErrorMessage) {
        this.notificationEventId = notificationEventId;
        this.sourceEventId = sourceEventId;
        this.clientId = clientId;
        this.eventType = eventType;
        this.contentSummary = contentSummary;
        this.payload = Map.copyOf(payload);
        this.createdAt = createdAt;
        this.deliveryStatus = deliveryStatus;
        this.deliveryAttempts = new ArrayList<>(deliveryAttempts);
        this.lastAttemptAt = deliveryAttempts.stream()
                .map(DeliveryAttempt::finishedAt)
                .max(Instant::compareTo)
                .orElse(null);
        this.nextRetryAt = null;
        this.finalErrorCode = finalErrorCode;
        this.finalErrorMessage = finalErrorMessage;
    }

    public synchronized void recordAttempt(DeliveryAttempt attempt, boolean finalAttempt) {
        deliveryAttempts.add(attempt);
        lastAttemptAt = attempt.finishedAt();
        if (attempt.result() == DeliveryResult.COMPLETED) {
            deliveryStatus = DeliveryStatus.COMPLETED;
            nextRetryAt = null;
            finalErrorCode = null;
            finalErrorMessage = null;
        } else if (finalAttempt) {
            deliveryStatus = DeliveryStatus.FAILED;
            nextRetryAt = null;
            finalErrorCode = attempt.errorCode();
            finalErrorMessage = attempt.errorMessage();
        } else {
            deliveryStatus = DeliveryStatus.RETRYING;
        }
    }

    public synchronized int nextAttemptNumber() {
        return deliveryAttempts.size() + 1;
    }

    public String notificationEventId() {
        return notificationEventId;
    }

    public String sourceEventId() {
        return sourceEventId;
    }

    public String clientId() {
        return clientId;
    }

    public String eventType() {
        return eventType;
    }

    public String contentSummary() {
        return contentSummary;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized DeliveryStatus deliveryStatus() {
        return deliveryStatus;
    }

    public synchronized int attemptCount() {
        return deliveryAttempts.size();
    }

    public synchronized Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public synchronized Instant nextRetryAt() {
        return nextRetryAt;
    }

    public synchronized String finalErrorCode() {
        return finalErrorCode;
    }

    public synchronized String finalErrorMessage() {
        return finalErrorMessage;
    }

    public synchronized List<DeliveryAttempt> deliveryAttempts() {
        return Collections.unmodifiableList(new ArrayList<>(deliveryAttempts));
    }
}
