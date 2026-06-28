package com.cobre.notifications.adapter.api;

import com.cobre.notifications.application.PageResult;
import com.cobre.notifications.domain.DeliveryAttempt;
import com.cobre.notifications.domain.NotificationEvent;

import java.util.Comparator;

import org.springframework.stereotype.Component;

@Component
class NotificationEventMapper {

    ApiResponses.ListResponse toListResponse(PageResult<NotificationEvent> page, String correlationId) {
        return new ApiResponses.ListResponse(
                page.data().stream().map(this::toSummary).toList(),
                new ApiResponses.PageResponse(
                        page.page(),
                        page.size(),
                        page.totalElements(),
                        page.totalPages()),
                correlationId);
    }

    ApiResponses.NotificationEventDetailResponse toDetailResponse(
            NotificationEvent event,
            String correlationId) {
        return new ApiResponses.NotificationEventDetailResponse(
                event.notificationEventId(),
                event.eventType(),
                event.contentSummary(),
                true,
                event.createdAt().toString(),
                event.deliveryStatus().toWireValue(),
                event.clientId(),
                event.attemptCount(),
                ApiResponses.instant(event.lastAttemptAt()),
                ApiResponses.instant(event.nextRetryAt()),
                event.finalErrorCode(),
                event.finalErrorMessage(),
                event.deliveryAttempts().stream()
                        .sorted(Comparator.comparingInt(DeliveryAttempt::attemptNumber))
                        .map(this::toAttempt)
                        .toList(),
                correlationId);
    }

    private ApiResponses.NotificationEventSummaryResponse toSummary(NotificationEvent event) {
        return new ApiResponses.NotificationEventSummaryResponse(
                event.notificationEventId(),
                event.eventType(),
                event.contentSummary(),
                true,
                event.createdAt().toString(),
                event.deliveryStatus().toWireValue(),
                event.clientId());
    }

    private ApiResponses.DeliveryAttemptResponse toAttempt(DeliveryAttempt attempt) {
        return new ApiResponses.DeliveryAttemptResponse(
                attempt.attemptId(),
                attempt.attemptNumber(),
                attempt.startedAt().toString(),
                attempt.finishedAt().toString(),
                attempt.result().toWireValue(),
                attempt.httpStatus(),
                attempt.latencyMs(),
                attempt.errorCode(),
                attempt.errorMessage());
    }
}
