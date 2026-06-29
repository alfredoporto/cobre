package com.cobre.notifications.application.usecase;

import com.cobre.notifications.application.exception.InvalidRequestException;
import com.cobre.notifications.application.model.AuthenticatedClient;
import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.port.out.NotificationEventRepositoryPort;
import com.cobre.notifications.application.port.out.WebhookDeliveryPort;
import com.cobre.notifications.application.port.out.WebhookDeliveryResult;
import com.cobre.notifications.domain.DeliveryAttempt;
import com.cobre.notifications.domain.DeliveryResult;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationEventServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2024-03-16T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void whenReplayReturnsNoDeliveryResults_shouldNotPersistEventState() {
        InMemoryNotificationEventRepository repository = new InMemoryNotificationEventRepository(failedEvent());
        FakeWebhookDeliveryPort webhook = new FakeWebhookDeliveryPort();
        webhook.enqueue(List.of());
        NotificationEventService service = new NotificationEventService(repository, webhook, CLOCK);

        assertThatThrownBy(() -> service.replay(client(), "EVT999", "corr-123"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Replay did not produce a delivery result");

        assertThat(webhook.firstAttemptNumbers()).containsExactly(2);
        assertThat(repository.savedEvents()).isEmpty();
        assertThat(repository.event("EVT999").attemptCount()).isEqualTo(1);
        assertThat(repository.event("EVT999").deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }

    @Test
    void whenFailedReplayIsReplayedAgain_shouldContinueAttemptNumbersFromSavedReplacement() {
        InMemoryNotificationEventRepository repository = new InMemoryNotificationEventRepository(failedEvent());
        FakeWebhookDeliveryPort webhook = new FakeWebhookDeliveryPort();
        webhook.enqueue(List.of(failed(400, false)));
        webhook.enqueue(List.of(completed(200)));
        NotificationEventService service = new NotificationEventService(repository, webhook, CLOCK);

        var failedReplay = service.replay(client(), "EVT999", "corr-123");
        var completedReplay = service.replay(client(), "EVT999", "corr-456");

        NotificationEvent updatedEvent = repository.event("EVT999");
        assertThat(failedReplay.replayStatus()).isEqualTo("failed");
        assertThat(completedReplay.replayStatus()).isEqualTo("completed");
        assertThat(webhook.firstAttemptNumbers()).containsExactly(2, 3);
        assertThat(repository.savedEvents()).hasSize(2);
        assertThat(updatedEvent.deliveryStatus()).isEqualTo(DeliveryStatus.COMPLETED);
        assertThat(updatedEvent.finalErrorCode()).isNull();
        assertThat(updatedEvent.deliveryAttempts())
                .extracting(DeliveryAttempt::attemptNumber)
                .containsExactly(1, 2, 3);
    }

    private static AuthenticatedClient client() {
        return new AuthenticatedClient("CLIENT999", Set.of(NotificationEventService.REPLAY_SCOPE));
    }

    private static NotificationEvent failedEvent() {
        DeliveryAttempt seedAttempt = new DeliveryAttempt(
                "ATT-SEED",
                "EVT999",
                1,
                Instant.parse("2024-03-15T11:20:18Z"),
                Instant.parse("2024-03-15T11:20:19Z"),
                DeliveryResult.FAILED,
                500,
                1000,
                "HTTP_500",
                "Initial fixture delivery failed");
        return new NotificationEvent(
                "EVT999",
                "EVT999",
                "CLIENT999",
                "credit_transfer",
                "Bank transfer received from Account ****4567 for $1,500.00",
                Map.of("event_id", "EVT999"),
                Instant.parse("2024-03-15T11:20:18Z"),
                DeliveryStatus.FAILED,
                List.of(seedAttempt),
                "HTTP_500",
                "Initial fixture delivery failed");
    }

    private static WebhookDeliveryResult completed(int statusCode) {
        return new WebhookDeliveryResult(DeliveryResult.COMPLETED, statusCode, 10, null, null, false);
    }

    private static WebhookDeliveryResult failed(int statusCode, boolean retryable) {
        return new WebhookDeliveryResult(
                DeliveryResult.FAILED,
                statusCode,
                10,
                "HTTP_" + statusCode,
                retryable ? "Webhook endpoint returned a transient error" : "Webhook endpoint returned a permanent error",
                retryable);
    }

    private static final class InMemoryNotificationEventRepository implements NotificationEventRepositoryPort {

        private final Map<String, NotificationEvent> eventsById = new LinkedHashMap<>();
        private final List<NotificationEvent> savedEvents = new ArrayList<>();

        private InMemoryNotificationEventRepository(NotificationEvent event) {
            eventsById.put(event.notificationEventId(), event);
        }

        @Override
        public List<NotificationEvent> findByClient(String clientId, NotificationEventQuery query) {
            return eventsById.values().stream()
                    .filter(event -> event.clientId().equals(clientId))
                    .toList();
        }

        @Override
        public long countByClient(String clientId, NotificationEventQuery query) {
            return findByClient(clientId, query).size();
        }

        @Override
        public Optional<NotificationEvent> findByClientAndId(String clientId, String notificationEventId) {
            NotificationEvent event = eventsById.get(notificationEventId);
            if (event == null || !event.clientId().equals(clientId)) {
                return Optional.empty();
            }
            return Optional.of(event);
        }

        @Override
        public NotificationEvent save(NotificationEvent notificationEvent) {
            eventsById.put(notificationEvent.notificationEventId(), notificationEvent);
            savedEvents.add(notificationEvent);
            return notificationEvent;
        }

        private NotificationEvent event(String notificationEventId) {
            return eventsById.get(notificationEventId);
        }

        private List<NotificationEvent> savedEvents() {
            return List.copyOf(savedEvents);
        }
    }

    private static final class FakeWebhookDeliveryPort implements WebhookDeliveryPort {

        private final Queue<List<WebhookDeliveryResult>> responses = new ArrayDeque<>();
        private final List<Integer> firstAttemptNumbers = new ArrayList<>();

        @Override
        public List<WebhookDeliveryResult> send(
                NotificationEvent notificationEvent,
                int firstAttemptNumber,
                String correlationId) {
            firstAttemptNumbers.add(firstAttemptNumber);
            if (responses.isEmpty()) {
                return List.of();
            }
            return responses.remove();
        }

        private void enqueue(List<WebhookDeliveryResult> results) {
            responses.add(results);
        }

        private List<Integer> firstAttemptNumbers() {
            return List.copyOf(firstAttemptNumbers);
        }
    }
}
