package com.cobre.notifications.domain;

import java.time.Instant;
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
}
