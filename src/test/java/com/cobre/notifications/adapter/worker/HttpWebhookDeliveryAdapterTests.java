package com.cobre.notifications.adapter.worker;

import com.cobre.notifications.configuration.WebhookDeliveryProperties;
import com.cobre.notifications.domain.DeliveryAttempt;
import com.cobre.notifications.domain.DeliveryResult;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class HttpWebhookDeliveryAdapterTests {

    @Test
    void sendsHeadersAndRetriesTransientResponses() {
        StubHttpClient httpClient = new StubHttpClient();
        httpClient.enqueue(500);
        httpClient.enqueue(200);
        HttpWebhookDeliveryAdapter adapter = new HttpWebhookDeliveryAdapter(
                properties(3),
                httpClient,
                new ObjectMapper());

        var results = adapter.send(event(), 2, "corr-123");

        assertThat(results).hasSize(2);
        assertThat(results.get(0).result()).isEqualTo(DeliveryResult.FAILED);
        assertThat(results.get(0).retryable()).isTrue();
        assertThat(results.get(1).result()).isEqualTo(DeliveryResult.COMPLETED);
        assertThat(httpClient.requests()).hasSize(2);
        assertThat(httpClient.requests().get(0).headers().firstValue("X-Cobre-Notification-Id")).contains("EVT999");
        assertThat(httpClient.requests().get(0).headers().firstValue("X-Cobre-Delivery-Attempt")).contains("2");
        assertThat(httpClient.requests().get(1).headers().firstValue("X-Cobre-Delivery-Attempt")).contains("3");
    }

    @Test
    void doesNotRetryPermanentResponses() {
        StubHttpClient httpClient = new StubHttpClient();
        httpClient.enqueue(400);
        HttpWebhookDeliveryAdapter adapter = new HttpWebhookDeliveryAdapter(
                properties(3),
                httpClient,
                new ObjectMapper());

        var results = adapter.send(event(), 1, "corr-123");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).httpStatus()).isEqualTo(400);
        assertThat(results.get(0).retryable()).isFalse();
        assertThat(httpClient.requests()).hasSize(1);
    }

    private static WebhookDeliveryProperties properties(int maxAttempts) {
        return new WebhookDeliveryProperties(
                URI.create("https://client.example/webhook"),
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                maxAttempts,
                Duration.ZERO);
    }

    private static NotificationEvent event() {
        DeliveryAttempt attempt = new DeliveryAttempt(
                "ATT-SEED",
                "EVT999",
                1,
                Instant.parse("2024-03-15T11:20:18Z"),
                Instant.parse("2024-03-15T11:20:19Z"),
                DeliveryResult.FAILED,
                500,
                1000,
                "HTTP_500",
                "Initial failure");
        return new NotificationEvent(
                "EVT999",
                "SRC999",
                "CLIENT999",
                "credit_transfer",
                "Bank transfer received",
                Map.of("content", "Bank transfer received"),
                Instant.parse("2024-03-15T11:20:18Z"),
                DeliveryStatus.FAILED,
                List.of(attempt),
                "HTTP_500",
                "Initial failure");
    }

    private static final class StubHttpClient extends HttpClient {

        private final Queue<Integer> statuses = new ConcurrentLinkedQueue<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        void enqueue(int status) {
            statuses.add(status);
        }

        List<HttpRequest> requests() {
            return List.copyOf(requests);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            requests.add(request);
            int status = statuses.isEmpty() ? 200 : statuses.remove();
            return new StubHttpResponse<>(request, status, null);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.completedFuture(new StubHttpResponse<>(request, 200, null));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private record StubHttpResponse<T>(
            HttpRequest request,
            int statusCode,
            T body) implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
