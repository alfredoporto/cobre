package com.cobre.notifications.domain;

import java.time.Instant;

public record DeliveryAttempt(
        String attemptId,
        String notificationEventId,
        int attemptNumber,
        Instant startedAt,
        Instant finishedAt,
        DeliveryResult result,
        Integer httpStatus,
        long latencyMs,
        String errorCode,
        String errorMessage) {
}
