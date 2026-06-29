package com.cobre.notifications.adapter.in.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class WebhookTestConfiguration {

    @Bean
    @Primary
    FakeWebhookDeliveryPort fakeWebhookDeliveryPort() {
        return new FakeWebhookDeliveryPort();
    }
}
