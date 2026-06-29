package com.cobre.notifications.adapter.in.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class NotificationEventListApiTests extends NotificationEventWebTestSupport {

    @Test
    void whenListingEvents_shouldReturnOnlyAuthenticatedClientEvents() throws Exception {
        Response response = perform(authorized(get("/notification_events"), "CLIENT001", "notification-events:read"));

        JsonNode json = response.body();
        assertThat(response.status()).isEqualTo(200);
        assertThat(json.path("data")).hasSize(4);
        assertThat(json.path("data").get(0).path("notification_event_id").asText()).isEqualTo("EVT010");
        assertThat(clientIds(json)).containsOnly("CLIENT001");
        assertThat(json.path("page").path("total_elements").asLong()).isEqualTo(4);
    }

    @Test
    void whenFilteringEvents_shouldApplyStatusAndDateRangeCriteria() throws Exception {
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
}
