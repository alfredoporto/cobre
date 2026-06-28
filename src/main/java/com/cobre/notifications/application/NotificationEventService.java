package com.cobre.notifications.application;

import com.cobre.notifications.application.port.NotificationEventRepositoryPort;
import com.cobre.notifications.application.port.WebhookDeliveryPort;
import com.cobre.notifications.application.port.WebhookDeliveryResult;
import com.cobre.notifications.domain.DeliveryAttempt;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class NotificationEventService {

    public static final String READ_SCOPE = "notification-events:read";
    public static final String REPLAY_SCOPE = "notification-events:replay";

    private final NotificationEventRepositoryPort repository;
    private final WebhookDeliveryPort webhookDeliveryPort;
    private final Clock clock;

    public NotificationEventService(
            NotificationEventRepositoryPort repository,
            WebhookDeliveryPort webhookDeliveryPort,
            Clock clock) {
        this.repository = repository;
        this.webhookDeliveryPort = webhookDeliveryPort;
        this.clock = clock;
    }

    public PageResult<NotificationEvent> list(AuthenticatedClient client, NotificationEventQuery query) {
        requireScope(client, READ_SCOPE);
        List<NotificationEvent> events = repository.findByClient(client.clientId(), query);
        long total = repository.countByClient(client.clientId(), query);
        return new PageResult<>(events, query.page(), query.size(), total);
    }

    public NotificationEvent detail(AuthenticatedClient client, String notificationEventId) {
        requireScope(client, READ_SCOPE);
        return repository.findByClientAndId(client.clientId(), notificationEventId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification event was not found"));
    }

    public ReplayResult replay(AuthenticatedClient client, String notificationEventId, String correlationId) {
        requireScope(client, REPLAY_SCOPE);
        NotificationEvent event = repository.findByClientAndId(client.clientId(), notificationEventId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification event was not found"));

        if (event.deliveryStatus() != DeliveryStatus.FAILED) {
            throw new InvalidRequestException("Only failed notification events can be replayed");
        }

        int firstAttemptNumber = event.nextAttemptNumber();
        List<WebhookDeliveryResult> results = webhookDeliveryPort.send(event, firstAttemptNumber, correlationId);
        if (results.isEmpty()) {
            throw new InvalidRequestException("Replay did not produce a delivery result");
        }
        WebhookDeliveryResult result = results.get(results.size() - 1);
        for (int index = 0; index < results.size(); index++) {
            WebhookDeliveryResult attemptResult = results.get(index);
            Instant finishedAt = clock.instant();
            Instant startedAt = finishedAt.minusMillis(Math.max(attemptResult.latencyMs(), 0));
            DeliveryAttempt attempt = new DeliveryAttempt(
                    "ATT-" + UUID.randomUUID(),
                    event.notificationEventId(),
                    firstAttemptNumber + index,
                    startedAt,
                    finishedAt,
                    attemptResult.result(),
                    attemptResult.httpStatus(),
                    attemptResult.latencyMs(),
                    attemptResult.errorCode(),
                    attemptResult.errorMessage());
            event.recordAttempt(attempt, index == results.size() - 1);
        }
        repository.save(event);

        String replayStatus = result.successful() ? "completed" : "failed";
        String message = result.successful() ? "Replay processed" : "Replay processed with failed delivery";
        return new ReplayResult(event.notificationEventId(), replayStatus, message);
    }

    private static void requireScope(AuthenticatedClient client, String scope) {
        if (!client.hasScope(scope)) {
            throw new ForbiddenOperationException("Missing required scope");
        }
    }
}
