package com.cobre.notifications.adapter.api;

import java.time.Instant;
import java.util.List;

final class ApiResponses {

    private ApiResponses() {
    }

    record NotificationEventSummaryResponse(
            String notification_event_id,
            String event_type,
            String content_summary,
            boolean payload_redacted,
            String created_at,
            String delivery_status,
            String client_id) {
    }

    record NotificationEventDetailResponse(
            String notification_event_id,
            String event_type,
            String content_summary,
            boolean payload_redacted,
            String created_at,
            String delivery_status,
            String client_id,
            int attempt_count,
            String last_attempt_at,
            String next_retry_at,
            String final_error_code,
            String final_error_message,
            List<DeliveryAttemptResponse> delivery_attempts,
            String correlation_id) {
    }

    record DeliveryAttemptResponse(
            String attempt_id,
            int attempt_number,
            String started_at,
            String finished_at,
            String result,
            Integer http_status,
            long latency_ms,
            String error_code,
            String error_message) {
    }

    record ListResponse(
            List<NotificationEventSummaryResponse> data,
            PageResponse page,
            String correlation_id) {
    }

    record PageResponse(
            int number,
            int size,
            long total_elements,
            int total_pages) {
    }

    record ReplayResponse(
            String notification_event_id,
            String replay_status,
            String message,
            String correlation_id) {
    }

    record ErrorEnvelope(ErrorResponse error) {
    }

    record ErrorResponse(String code, String message, String correlation_id) {
    }

    static String instant(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
