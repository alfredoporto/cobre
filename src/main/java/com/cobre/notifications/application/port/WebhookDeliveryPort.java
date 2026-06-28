package com.cobre.notifications.application.port;

import com.cobre.notifications.domain.NotificationEvent;

import java.util.List;

public interface WebhookDeliveryPort {

    List<WebhookDeliveryResult> send(NotificationEvent notificationEvent, int firstAttemptNumber, String correlationId);
}
