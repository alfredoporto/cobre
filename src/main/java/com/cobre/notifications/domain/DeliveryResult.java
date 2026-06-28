package com.cobre.notifications.domain;

import java.util.Locale;

public enum DeliveryResult {
    COMPLETED,
    FAILED;

    public String toWireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
