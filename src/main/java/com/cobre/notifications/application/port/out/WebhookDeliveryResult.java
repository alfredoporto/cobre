package com.cobre.notifications.application.port.out;

import com.cobre.notifications.domain.DeliveryResult;

public record WebhookDeliveryResult(
        DeliveryResult result,
        Integer httpStatus,
        long latencyMs,
        String errorCode,
        String errorMessage,
        boolean retryable) {

    public boolean successful() {
        return result == DeliveryResult.COMPLETED;
    }
}
