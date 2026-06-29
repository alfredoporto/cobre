package com.cobre.notifications.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventTests {

    @Test
    void whenAppendingSuccessfulAttempt_shouldReturnCompletedEventWithoutMutatingOriginal() {
        NotificationEvent original = failedEvent();
        DeliveryAttempt replayAttempt = attempt(
                "ATT-REPLAY-1",
                2,
                DeliveryResult.COMPLETED,
                200,
                null,
                null);

        NotificationEvent updated = original.withDeliveryAttemptsAppended(List.of(replayAttempt));

        assertThat(original.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(original.attemptCount()).isEqualTo(1);
        assertThat(original.finalErrorCode()).isEqualTo("HTTP_500");
        assertThat(updated.deliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(updated.attemptCount()).isEqualTo(2);
        assertThat(updated.finalErrorCode()).isNull();
        assertThat(updated.finalErrorMessage()).isNull();
        assertThat(updated.lastAttemptAt()).isEqualTo(replayAttempt.finishedAt());
    }

    @Test
    void whenAppendingFailedAttempt_shouldReturnFailedEventWithFinalError() {
        NotificationEvent original = failedEvent();
        DeliveryAttempt replayAttempt = attempt(
                "ATT-REPLAY-1",
                2,
                DeliveryResult.FAILED,
                400,
                "HTTP_400",
                "Webhook endpoint returned a permanent error");

        NotificationEvent updated = original.withDeliveryAttemptsAppended(List.of(replayAttempt));

        assertThat(original.attemptCount()).isEqualTo(1);
        assertThat(original.finalErrorCode()).isEqualTo("HTTP_500");
        assertThat(updated.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(updated.attemptCount()).isEqualTo(2);
        assertThat(updated.finalErrorCode()).isEqualTo("HTTP_400");
        assertThat(updated.finalErrorMessage()).isEqualTo("Webhook endpoint returned a permanent error");
        assertThat(updated.deliveryAttempts().get(1)).isEqualTo(replayAttempt);
    }

    @Test
    void whenReadingAttempts_shouldExposeImmutableAttemptList() {
        NotificationEvent original = failedEvent();

        assertThatThrownBy(() -> original.deliveryAttempts().add(attempt(
                "ATT-ILLEGAL",
                2,
                DeliveryResult.COMPLETED,
                200,
                null,
                null))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void whenReadingPayload_shouldExposeDeeplyImmutablePayload() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("account_last4", "4567");
        List<Object> tags = new ArrayList<>();
        tags.add("original");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nested", nested);
        payload.put("tags", tags);

        NotificationEvent event = new NotificationEvent(
                "EVT999",
                "EVT999",
                "CLIENT999",
                "credit_transfer",
                "Bank transfer received from Account ****4567 for $1,500.00",
                payload,
                Instant.parse("2024-03-15T11:20:18Z"),
                DeliveryStatus.FAILED,
                List.of(attempt(
                        "ATT-SEED",
                        1,
                        DeliveryResult.FAILED,
                        500,
                        "HTTP_500",
                        "Initial fixture delivery failed")),
                "HTTP_500",
                "Initial fixture delivery failed");

        nested.put("account_last4", "9999");
        tags.add("mutated");

        assertThat(event.payload()).containsOnlyKeys("nested", "tags");
        assertThat(nestedPayload(event).get("account_last4")).isEqualTo("4567");
        assertThat(tagsPayload(event)).containsExactly("original");
        assertThatThrownBy(() -> event.payload().put("illegal", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> nestedPayload(event).put("illegal", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> tagsPayload(event).add("illegal"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void whenAppendingAttemptForDifferentEvent_shouldRejectAttempt() {
        NotificationEvent original = failedEvent();
        DeliveryAttempt attemptForAnotherEvent = new DeliveryAttempt(
                "ATT-OTHER",
                "EVT-OTHER",
                2,
                Instant.parse("2024-03-15T12:20:18Z"),
                Instant.parse("2024-03-15T12:20:19Z"),
                DeliveryResult.COMPLETED,
                200,
                1000,
                null,
                null);

        assertThatThrownBy(() -> original.withDeliveryAttemptsAppended(List.of(attemptForAnotherEvent)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Delivery attempt belongs to another notification event");
    }

    @Test
    void whenAppendingNonSequentialAttempt_shouldRejectAttempt() {
        NotificationEvent original = failedEvent();
        DeliveryAttempt skippedAttemptNumber = attempt(
                "ATT-REPLAY-3",
                3,
                DeliveryResult.COMPLETED,
                200,
                null,
                null);

        assertThatThrownBy(() -> original.withDeliveryAttemptsAppended(List.of(skippedAttemptNumber)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Delivery attempt numbers must be sequential");
    }

    private static NotificationEvent failedEvent() {
        DeliveryAttempt seedAttempt = attempt(
                "ATT-SEED",
                1,
                DeliveryResult.FAILED,
                500,
                "HTTP_500",
                "Initial fixture delivery failed");
        return new NotificationEvent(
                "EVT999",
                "EVT999",
                "CLIENT999",
                "credit_transfer",
                "Bank transfer received from Account ****4567 for $1,500.00",
                Map.of("event_id", "EVT999"),
                Instant.parse("2024-03-15T11:20:18Z"),
                DeliveryStatus.FAILED,
                List.of(seedAttempt),
                "HTTP_500",
                "Initial fixture delivery failed");
    }

    private static DeliveryAttempt attempt(
            String attemptId,
            int attemptNumber,
            DeliveryResult result,
            Integer httpStatus,
            String errorCode,
            String errorMessage) {
        Instant startedAt = Instant.parse("2024-03-15T12:20:18Z").plusSeconds(attemptNumber);
        return new DeliveryAttempt(
                attemptId,
                "EVT999",
                attemptNumber,
                startedAt,
                startedAt.plusMillis(100),
                result,
                httpStatus,
                100,
                errorCode,
                errorMessage);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> nestedPayload(NotificationEvent event) {
        return (Map<Object, Object>) event.payload().get("nested");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> tagsPayload(NotificationEvent event) {
        return (List<Object>) event.payload().get("tags");
    }
}
