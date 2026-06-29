package com.cobre.notifications.configuration;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cobre.notifications.adapter.in.web.CorrelationIdFilter;
import com.cobre.notifications.adapter.in.security.ClientHeaderAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebhookDeliveryProperties.class)
public class ApplicationConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HttpClient webhookHttpClient(WebhookDeliveryProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration(CorrelationIdFilter filter) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ClientHeaderAuthenticationFilter> clientHeaderAuthenticationFilterRegistration(
            ClientHeaderAuthenticationFilter filter) {
        FilterRegistrationBean<ClientHeaderAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
