package com.cobre.notifications.application;

public record ReplayResult(
        String notificationEventId,
        String replayStatus,
        String message) {
}
