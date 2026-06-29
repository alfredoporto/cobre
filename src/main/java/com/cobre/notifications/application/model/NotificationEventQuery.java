package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.DeliveryStatus;

import java.time.Instant;
import java.util.Optional;

public record NotificationEventQuery(
        Optional<Instant> createdFrom,
        Optional<Instant> createdTo,
        Optional<DeliveryStatus> deliveryStatus,
        int page,
        int size) {
}
