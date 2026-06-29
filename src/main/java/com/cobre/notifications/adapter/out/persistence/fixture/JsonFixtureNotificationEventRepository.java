package com.cobre.notifications.adapter.out.persistence.fixture;

import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.port.out.NotificationEventRepositoryPort;
import com.cobre.notifications.domain.DeliveryAttempt;
import com.cobre.notifications.domain.DeliveryResult;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JsonFixtureNotificationEventRepository implements NotificationEventRepositoryPort {

    private static final Pattern ACCOUNT_PATTERN = Pattern.compile("Account #(\\d{4,})");

    private final Resource fixtureResource;
    private final ObjectMapper objectMapper;
    private final Map<String, NotificationEvent> eventsById = new ConcurrentHashMap<>();

    public JsonFixtureNotificationEventRepository(
            @Value("${cobre.notifications.fixture:classpath:notification_events.json}") Resource fixtureResource,
            ObjectMapper objectMapper) {
        this.fixtureResource = fixtureResource;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadFixtures() throws IOException {
        try (InputStream inputStream = fixtureResource.getInputStream()) {
            JsonNode root = objectMapper.readTree(inputStream);
            for (JsonNode node : root.path("events")) {
                FixtureEvent fixtureEvent = FixtureEvent.from(node);
                NotificationEvent event = toDomain(fixtureEvent);
                eventsById.put(event.notificationEventId(), event);
            }
        } catch (JacksonException exception) {
            throw new IOException("Could not read notification event fixture", exception);
        }
    }

    @Override
    public List<NotificationEvent> findByClient(String clientId, NotificationEventQuery query) {
        return matching(clientId, query).stream()
                .skip((long) query.page() * query.size())
                .limit(query.size())
                .toList();
    }

    @Override
    public long countByClient(String clientId, NotificationEventQuery query) {
        return matching(clientId, query).size();
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
        return notificationEvent;
    }

    private List<NotificationEvent> matching(String clientId, NotificationEventQuery query) {
        return eventsById.values().stream()
                .filter(event -> event.clientId().equals(clientId))
                .filter(event -> query.createdFrom().map(from -> !event.createdAt().isBefore(from)).orElse(true))
                .filter(event -> query.createdTo().map(to -> !event.createdAt().isAfter(to)).orElse(true))
                .filter(event -> query.deliveryStatus().map(status -> event.deliveryStatus() == status).orElse(true))
                .sorted(Comparator.comparing(NotificationEvent::createdAt).reversed()
                        .thenComparing(NotificationEvent::notificationEventId, Comparator.reverseOrder()))
                .toList();
    }

    private static NotificationEvent toDomain(FixtureEvent fixtureEvent) {
        DeliveryStatus status = DeliveryStatus.fromWireValue(fixtureEvent.deliveryStatus());
        Instant createdAt = Instant.parse(fixtureEvent.deliveryDate());
        DeliveryAttempt seedAttempt = seedAttempt(fixtureEvent, createdAt, status);
        String finalErrorCode = status == DeliveryStatus.FAILED ? "WEBHOOK_DELIVERY_FAILED" : null;
        String finalErrorMessage = status == DeliveryStatus.FAILED ? "Initial fixture delivery failed" : null;
        return new NotificationEvent(
                fixtureEvent.eventId(),
                fixtureEvent.eventId(),
                fixtureEvent.clientId(),
                fixtureEvent.eventType(),
                redactContent(fixtureEvent.content()),
                payloadFor(fixtureEvent),
                createdAt,
                status,
                new ArrayList<>(List.of(seedAttempt)),
                finalErrorCode,
                finalErrorMessage);
    }

    private static DeliveryAttempt seedAttempt(FixtureEvent fixtureEvent, Instant createdAt, DeliveryStatus status) {
        boolean completed = status == DeliveryStatus.COMPLETED;
        return new DeliveryAttempt(
                "ATT-SEED-" + fixtureEvent.eventId(),
                fixtureEvent.eventId(),
                1,
                createdAt,
                createdAt.plusMillis(completed ? 125 : 500),
                completed ? DeliveryResult.COMPLETED : DeliveryResult.FAILED,
                completed ? 200 : 500,
                completed ? 125 : 500,
                completed ? null : "WEBHOOK_DELIVERY_FAILED",
                completed ? null : "Initial fixture delivery failed");
    }

    private static Map<String, Object> payloadFor(FixtureEvent fixtureEvent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", fixtureEvent.eventId());
        payload.put("event_type", fixtureEvent.eventType());
        payload.put("content", fixtureEvent.content());
        payload.put("delivery_date", fixtureEvent.deliveryDate());
        payload.put("delivery_status", fixtureEvent.deliveryStatus());
        payload.put("client_id", fixtureEvent.clientId());
        return payload;
    }

    private static String redactContent(String content) {
        return ACCOUNT_PATTERN.matcher(content).replaceAll("Account ****$1");
    }

    private record FixtureEvent(
            String eventId,
            String eventType,
            String content,
            String deliveryDate,
            String deliveryStatus,
            String clientId) {

        static FixtureEvent from(JsonNode node) {
            return new FixtureEvent(
                    node.path("event_id").asText(),
                    node.path("event_type").asText(),
                    node.path("content").asText(),
                    node.path("delivery_date").asText(),
                    node.path("delivery_status").asText(),
                    node.path("client_id").asText());
        }
    }
}
