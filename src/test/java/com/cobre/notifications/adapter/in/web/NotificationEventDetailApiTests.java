package com.cobre.notifications.adapter.in.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class NotificationEventDetailApiTests extends NotificationEventWebTestSupport {

    @Test
    void whenFetchingOwnedEvent_shouldReturnRedactedDetailsAndAttempts() throws Exception {
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
    void whenFetchingCrossClientEvent_shouldReturnNotFound() throws Exception {
        Response response = perform(authorized(
                get("/notification_events/EVT003"),
                "CLIENT001",
                "notification-events:read"));

        JsonNode json = response.body();
        assertThat(response.status()).isEqualTo(404);
        assertThat(json.path("error").path("code").asText()).isEqualTo("not_found");
        assertThat(json.path("error").path("correlation_id").asText()).isNotBlank();
    }
}
