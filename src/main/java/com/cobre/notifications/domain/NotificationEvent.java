package com.cobre.notifications.domain;

import java.time.Instant;
import java.util.ArrayList;
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
    private final DeliveryStatus deliveryStatus;
    private final Instant lastAttemptAt;
    private final Instant nextRetryAt;
    private final String finalErrorCode;
    private final String finalErrorMessage;
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
        this(
                notificationEventId,
                sourceEventId,
                clientId,
                eventType,
                contentSummary,
                payload,
                createdAt,
                deliveryStatus,
                lastAttemptAt(deliveryAttempts),
                null,
                finalErrorCode,
                finalErrorMessage,
                deliveryAttempts);
    }

    private NotificationEvent(
            String notificationEventId,
            String sourceEventId,
            String clientId,
            String eventType,
            String contentSummary,
            Map<String, Object> payload,
            Instant createdAt,
            DeliveryStatus deliveryStatus,
            Instant lastAttemptAt,
            Instant nextRetryAt,
            String finalErrorCode,
            String finalErrorMessage,
            List<DeliveryAttempt> deliveryAttempts) {
        this.notificationEventId = notificationEventId;
        this.sourceEventId = sourceEventId;
        this.clientId = clientId;
        this.eventType = eventType;
        this.contentSummary = contentSummary;
        this.payload = Map.copyOf(payload);
        this.createdAt = createdAt;
        this.deliveryStatus = deliveryStatus;
        this.deliveryAttempts = List.copyOf(deliveryAttempts);
        this.lastAttemptAt = lastAttemptAt;
        this.nextRetryAt = nextRetryAt;
        this.finalErrorCode = finalErrorCode;
        this.finalErrorMessage = finalErrorMessage;
    }

    public NotificationEvent withDeliveryAttemptsAppended(List<DeliveryAttempt> attempts) {
        if (attempts.isEmpty()) {
            return this;
        }

        List<DeliveryAttempt> updatedAttempts = new ArrayList<>(deliveryAttempts);
        updatedAttempts.addAll(attempts);
        DeliveryAttempt finalAttempt = attempts.get(attempts.size() - 1);
        DeliveryStatus updatedStatus = finalAttempt.result() == DeliveryResult.COMPLETED
                ? DeliveryStatus.COMPLETED
                : DeliveryStatus.FAILED;
        String updatedFinalErrorCode = finalAttempt.result() == DeliveryResult.COMPLETED
                ? null
                : finalAttempt.errorCode();
        String updatedFinalErrorMessage = finalAttempt.result() == DeliveryResult.COMPLETED
                ? null
                : finalAttempt.errorMessage();

        return new NotificationEvent(
                notificationEventId,
                sourceEventId,
                clientId,
                eventType,
                contentSummary,
                payload,
                createdAt,
                updatedStatus,
                lastAttemptAt(updatedAttempts),
                null,
                updatedFinalErrorCode,
                updatedFinalErrorMessage,
                updatedAttempts);
    }

    public int nextAttemptNumber() {
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

    public DeliveryStatus deliveryStatus() {
        return deliveryStatus;
    }

    public int attemptCount() {
        return deliveryAttempts.size();
    }

    public Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public String finalErrorCode() {
        return finalErrorCode;
    }

    public String finalErrorMessage() {
        return finalErrorMessage;
    }

    public List<DeliveryAttempt> deliveryAttempts() {
        return deliveryAttempts;
    }

    private static Instant lastAttemptAt(List<DeliveryAttempt> deliveryAttempts) {
        return deliveryAttempts.stream()
                .map(DeliveryAttempt::finishedAt)
                .max(Instant::compareTo)
                .orElse(null);
    }
}
