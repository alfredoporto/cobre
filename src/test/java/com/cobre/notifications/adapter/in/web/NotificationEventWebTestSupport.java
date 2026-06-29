package com.cobre.notifications.adapter.in.web;

import com.cobre.notifications.application.port.out.WebhookDeliveryResult;
import com.cobre.notifications.domain.DeliveryResult;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(WebhookTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
abstract class NotificationEventWebTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    protected FakeWebhookDeliveryPort webhook;

    @BeforeEach
    void resetWebhook() {
        webhook.reset();
    }

    protected Response perform(MockHttpServletRequestBuilder request) throws Exception {
        var result = mockMvc.perform(request).andReturn().getResponse();
        return new Response(result.getStatus(), OBJECT_MAPPER.readTree(result.getContentAsString()));
    }

    protected static MockHttpServletRequestBuilder authorized(
            MockHttpServletRequestBuilder request,
            String clientId,
            String scopes) {
        return request.header("X-Client-Id", clientId)
                .header("X-Scopes", scopes);
    }

    protected static List<String> ids(JsonNode json) {
        List<String> ids = new ArrayList<>();
        for (JsonNode item : json.path("data")) {
            ids.add(item.path("notification_event_id").asText());
        }
        return ids;
    }

    protected static List<String> clientIds(JsonNode json) {
        List<String> clientIds = new ArrayList<>();
        for (JsonNode item : json.path("data")) {
            clientIds.add(item.path("client_id").asText());
        }
        return clientIds;
    }

    protected static WebhookDeliveryResult completed(int statusCode) {
        return new WebhookDeliveryResult(DeliveryResult.COMPLETED, statusCode, 10, null, null, false);
    }

    protected static WebhookDeliveryResult failed(int statusCode, boolean retryable) {
        return new WebhookDeliveryResult(
                DeliveryResult.FAILED,
                statusCode,
                10,
                "HTTP_" + statusCode,
                retryable ? "Webhook endpoint returned a transient error" : "Webhook endpoint returned a permanent error",
                retryable);
    }

    protected record Response(int status, JsonNode body) {
    }
}
