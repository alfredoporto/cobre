package com.cobre.notifications.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class NotificationEventSecurityAndValidationApiTests extends NotificationEventWebTestSupport {

    @Test
    void whenScopeOrAuthenticationIsMissing_shouldReturnSecurityErrors() throws Exception {
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
    void whenQueryParametersAreInvalid_shouldReturnBadRequest() throws Exception {
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
}
