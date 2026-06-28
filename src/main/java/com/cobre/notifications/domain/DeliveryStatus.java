package com.cobre.notifications.domain;

import java.util.Locale;

public enum DeliveryStatus {
    PENDING,
    IN_PROGRESS,
    RETRYING,
    COMPLETED,
    FAILED;

    public static DeliveryStatus fromWireValue(String value) {
        return DeliveryStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String toWireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
