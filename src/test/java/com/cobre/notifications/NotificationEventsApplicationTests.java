package com.cobre.notifications;

import com.cobre.notifications.application.port.WebhookDeliveryPort;
import com.cobre.notifications.application.port.WebhookDeliveryResult;
import com.cobre.notifications.domain.DeliveryResult;
import com.cobre.notifications.domain.NotificationEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class NotificationEventsApplicationTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FakeWebhookDeliveryPort webhook;

    @BeforeEach
    void resetWebhook() {
        webhook.reset();
    }

    @Test
    void listsOnlyEventsOwnedByTheAuthenticatedClient() throws Exception {
        Response response = perform(authorized(get("/notification_events"), "CLIENT001", "notification-events:read"));

        JsonNode json = response.body();
        assertThat(response.status()).isEqualTo(200);
        assertThat(json.path("data")).hasSize(4);
        assertThat(json.path("data").get(0).path("notification_event_id").asText()).isEqualTo("EVT010");
        assertThat(clientIds(json)).containsOnly("CLIENT001");
        assertThat(json.path("page").path("total_elements").asLong()).isEqualTo(4);
    }

    @Test
    void filtersByFailedStatusAndDateRange() throws Exception {
        Response failed = perform(authorized(
                get("/notification_events?delivery_status=failed"),
                "CLIENT003",
                "notification-events:read"));

        assertThat(failed.status()).isEqualTo(200);
        assertThat(ids(failed.body())).containsExactly("EVT009", "EVT005");

        Response byDate = perform(authorized(
                get("/notification_events?created_from=2024-03-15T11:00:00Z&created_to=2024-03-15T16:20:00Z"),
                "CLIENT002",
                "notification-events:read"));

        assertThat(byDate.status()).isEqualTo(200);
        assertThat(ids(byDate.body())).containsExactly("EVT008", "EVT004", "EVT003");
    }

    @Test
    void returnsOwnedEventDetailsWithRedactedContentAndAttempts() throws Exception {
        Response response = perform(authorized(
                get("/notification_events/EVT003"),
                "CLIENT002",
                "notification-events:read"));

        JsonNode json = response.body();
        assertThat(response.status()).isEqualTo(200);
        assertThat(json.path("notification_event_id").asText()).isEqualTo("EVT003");
        assertThat(json.path("content_summary").asText()).contains("Account ****4567");
        assertThat(json.path("payload_redacted").asBoolean()).isTrue();
        assertThat(json.path("delivery_status").asText()).isEqualTo("failed");
        assertThat(json.path("attempt_count").asInt()).isEqualTo(1);
        assertThat(json.path("delivery_attempts")).hasSize(1);
    }

    @Test
    void hidesCrossClientEventDetailsAsNotFound() throws Exception {
        Response response = perform(authorized(
                get("/notification_events/EVT003"),
                "CLIENT001",
                "notification-events:read"));

        JsonNode json = response.body();
        assertThat(response.status()).isEqualTo(404);
        assertThat(json.path("error").path("code").asText()).isEqualTo("not_found");
        assertThat(json.path("error").path("correlation_id").asText()).isNotBlank();
    }

    @Test
    void replaysFailedEventAndStoresCompletedAttempt() throws Exception {
        webhook.enqueue(completed(200));

        Response replay = perform(authorized(
                post("/notification_events/EVT003/replay"),
                "CLIENT002",
                "notification-events:read,notification-events:replay"));
        JsonNode replayJson = replay.body();

        assertThat(replay.status()).isEqualTo(202);
        assertThat(replayJson.path("notification_event_id").asText()).isEqualTo("EVT003");
        assertThat(replayJson.path("replay_status").asText()).isEqualTo("completed");
        assertThat(webhook.deliveries()).containsExactly("EVT003#2");

        Response detail = perform(authorized(
                get("/notification_events/EVT003"),
                "CLIENT002",
                "notification-events:read"));
        JsonNode detailJson = detail.body();

        assertThat(detailJson.path("delivery_status").asText()).isEqualTo("completed");
        assertThat(detailJson.path("attempt_count").asInt()).isEqualTo(2);
        assertThat(detailJson.path("delivery_attempts").get(1).path("http_status").asInt()).isEqualTo(200);
    }

    @Test
    void retriesTransientWebhookFailuresAndStoresEachAttempt() throws Exception {
        webhook.enqueue(failed(500, true));
        webhook.enqueue(failed(500, true));
        webhook.enqueue(completed(200));

        Response replay = perform(authorized(
                post("/notification_events/EVT005/replay"),
                "CLIENT003",
                "notification-events:read,notification-events:replay"));
        JsonNode replayJson = replay.body();

        assertThat(replay.status()).isEqualTo(202);
        assertThat(replayJson.path("replay_status").asText()).isEqualTo("completed");
        assertThat(webhook.deliveries()).containsExactly("EVT005#2");

        JsonNode detailJson = perform(authorized(
                get("/notification_events/EVT005"),
                "CLIENT003",
                "notification-events:read")).body();
        assertThat(detailJson.path("delivery_status").asText()).isEqualTo("completed");
        assertThat(detailJson.path("attempt_count").asInt()).isEqualTo(4);
        assertThat(detailJson.path("delivery_attempts").get(1).path("http_status").asInt()).isEqualTo(500);
        assertThat(detailJson.path("delivery_attempts").get(3).path("http_status").asInt()).isEqualTo(200);
    }

    @Test
    void permanentWebhookFailureDoesNotRetryAndLeavesEventFailed() throws Exception {
        webhook.enqueue(failed(400, false));

        Response replay = perform(authorized(
                post("/notification_events/EVT009/replay"),
                "CLIENT003",
                "notification-events:read,notification-events:replay"));
        JsonNode replayJson = replay.body();

        assertThat(replay.status()).isEqualTo(202);
        assertThat(replayJson.path("replay_status").asText()).isEqualTo("failed");
        assertThat(webhook.deliveries()).containsExactly("EVT009#2");

        JsonNode detailJson = perform(authorized(
                get("/notification_events/EVT009"),
                "CLIENT003",
                "notification-events:read")).body();
        assertThat(detailJson.path("delivery_status").asText()).isEqualTo("failed");
        assertThat(detailJson.path("attempt_count").asInt()).isEqualTo(2);
        assertThat(detailJson.path("final_error_code").asText()).isEqualTo("HTTP_400");
    }

    @Test
    void rejectsReplayForCompletedEvent() throws Exception {
        Response replay = perform(authorized(
                post("/notification_events/EVT004/replay"),
                "CLIENT002",
                "notification-events:read,notification-events:replay"));

        assertThat(replay.status()).isEqualTo(400);
        assertThat(replay.body().path("error").path("code").asText()).isEqualTo("bad_request");
        assertThat(webhook.deliveries()).isEmpty();
    }

    @Test
    void rejectsMissingScopeAndMissingAuthentication() throws Exception {
        Response missingScope = perform(authorized(
                get("/notification_events"),
                "CLIENT001",
                "notification-events:replay"));

        assertThat(missingScope.status()).isEqualTo(403);
        assertThat(missingScope.body().path("error").path("code").asText()).isEqualTo("forbidden");

        Response missingAuth = perform(get("/notification_events"));

        assertThat(missingAuth.status()).isEqualTo(401);
        assertThat(missingAuth.body().path("error").path("code").asText()).isEqualTo("unauthorized");
    }

    @Test
    void validatesQueryParameters() throws Exception {
        assertBadRequest("/notification_events?delivery_status=unknown");
        assertBadRequest("/notification_events?created_from=not-a-date");
        assertBadRequest("/notification_events?size=101");
        assertBadRequest("/notification_events?page=-1");
    }

    private void assertBadRequest(String path) throws Exception {
        Response response = perform(authorized(get(path), "CLIENT001", "notification-events:read"));
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().path("error").path("code").asText()).isEqualTo("bad_request");
    }

    private Response perform(MockHttpServletRequestBuilder request) throws Exception {
        var result = mockMvc.perform(request).andReturn().getResponse();
        return new Response(result.getStatus(), OBJECT_MAPPER.readTree(result.getContentAsString()));
    }

    private static MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder request,
            String clientId,
            String scopes) {
        return request.header("X-Client-Id", clientId)
                .header("X-Scopes", scopes);
    }

    private static List<String> ids(JsonNode json) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : json.path("data")) {
            ids.add(item.path("notification_event_id").asText());
        }
        return ids;
    }

    private static List<String> clientIds(JsonNode json) {
        List<String> clientIds = new ArrayList<>();
        for (JsonNode item : json.path("data")) {
            clientIds.add(item.path("client_id").asText());
        }
        return clientIds;
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

    private record Response(int status, JsonNode body) {
    }

    @TestConfiguration
    static class WebhookTestConfiguration {

        @Bean
        @Primary
        FakeWebhookDeliveryPort fakeWebhookDeliveryPort() {
            return new FakeWebhookDeliveryPort();
        }
    }

    static class FakeWebhookDeliveryPort implements WebhookDeliveryPort {

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
                results.add(completed(200));
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
}
