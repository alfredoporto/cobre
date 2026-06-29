package com.cobre.notifications.application.model;

public record ReplayResult(
        String notificationEventId,
        String replayStatus,
        String message) {
}
