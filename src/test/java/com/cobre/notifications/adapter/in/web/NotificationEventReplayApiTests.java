package com.cobre.notifications.adapter.in.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class NotificationEventReplayApiTests extends NotificationEventWebTestSupport {

    @Test
    void whenReplayingFailedEvent_shouldStoreCompletedAttempt() throws Exception {
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
    void whenReplayHasTransientFailures_shouldStoreEachAttemptAndEventuallyComplete() throws Exception {
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
    void whenReplayHasPermanentFailure_shouldStoreFailureWithoutRetry() throws Exception {
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
    void whenReplayingCompletedEvent_shouldReturnBadRequest() throws Exception {
        Response replay = perform(authorized(
                post("/notification_events/EVT004/replay"),
                "CLIENT002",
                "notification-events:read,notification-events:replay"));

        assertThat(replay.status()).isEqualTo(400);
        assertThat(replay.body().path("error").path("code").asText()).isEqualTo("bad_request");
        assertThat(webhook.deliveries()).isEmpty();
    }
}
