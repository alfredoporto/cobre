package com.cobre.notifications.adapter.in.web;

import com.cobre.notifications.application.port.out.WebhookDeliveryPort;
import com.cobre.notifications.application.port.out.WebhookDeliveryResult;
import com.cobre.notifications.domain.NotificationEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

class FakeWebhookDeliveryPort implements WebhookDeliveryPort {

    private final ConcurrentLinkedQueue<WebhookDeliveryResult> responses = new ConcurrentLinkedQueue<>();
    private final List<String> deliveries = new ArrayList<>();

    @Override
    public List<WebhookDeliveryResult> send(
            NotificationEvent notificationEvent,
            int firstAttemptNumber,
            String correlationId) {
        deliveries.add(notificationEvent.notificationEventId() + "#" + firstAttemptNumber);
        List<WebhookDeliveryResult> results = new ArrayList<>();
        WebhookDeliveryResult response;
        while ((response = responses.poll()) != null) {
            results.add(response);
            if (response.successful() || !response.retryable()) {
                return results;
            }
        }
        if (results.isEmpty()) {
            results.add(NotificationEventWebTestSupport.completed(200));
        }
        return results;
    }

    void enqueue(WebhookDeliveryResult response) {
        responses.add(response);
    }

    synchronized List<String> deliveries() {
        return List.copyOf(deliveries);
    }

    synchronized void reset() {
        responses.clear();
        deliveries.clear();
    }
}
