package com.cobre.notifications.adapter.in.web;

import com.cobre.notifications.application.model.AuthenticatedClient;
import com.cobre.notifications.application.exception.InvalidRequestException;
import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.application.usecase.NotificationEventService;
import com.cobre.notifications.application.model.PageResult;
import com.cobre.notifications.application.model.ReplayResult;
import com.cobre.notifications.adapter.in.security.ClientPrincipal;
import com.cobre.notifications.domain.DeliveryStatus;
import com.cobre.notifications.domain.NotificationEvent;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the notification-event self-service API.
 *
 * <p>This controller keeps transport concerns at the edge: it parses query parameters, obtains the
 * authenticated client from the local header-auth filter, delegates business rules to the application
 * service, and maps domain objects to redacted response DTOs.
 */
@RestController
public class NotificationEventController {

    private final NotificationEventService service;
    private final NotificationEventMapper mapper;

    public NotificationEventController(NotificationEventService service, NotificationEventMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /**
     * Lists notification events owned by the authenticated local client.
     *
     * <p>The local assessment identity is resolved from {@code X-Client-Id}; callers cannot override
     * tenancy with a query parameter. Results can be filtered by creation timestamp and delivery status.
     */
    @GetMapping("/notification_events")
    ApiResponses.ListResponse list(
            @RequestParam(name = "created_from", required = false) String createdFrom,
            @RequestParam(name = "created_to", required = false) String createdTo,
            @RequestParam(name = "delivery_status", required = false) String deliveryStatus,
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            Authentication authentication,
            HttpServletRequest request) {
        NotificationEventQuery query = parseQuery(createdFrom, createdTo, deliveryStatus, page, size);
        PageResult<NotificationEvent> events = service.list(currentClient(authentication), query);
        return mapper.toListResponse(events, CorrelationIdFilter.current(request));
    }

    /**
     * Returns one notification event detail if it belongs to the authenticated local client.
     *
     * <p>Missing and cross-client event IDs are both returned as {@code 404} to avoid leaking
     * another client's notification identifiers.
     */
    @GetMapping("/notification_events/{notification_event_id}")
    ApiResponses.NotificationEventDetailResponse detail(
            @PathVariable("notification_event_id") String notificationEventId,
            Authentication authentication,
            HttpServletRequest request) {
        NotificationEvent event = service.detail(currentClient(authentication), notificationEventId);
        return mapper.toDetailResponse(event, CorrelationIdFilter.current(request));
    }

    /**
     * Replays a terminal failed notification event through the configured webhook endpoint.
     *
     * <p>The route intentionally uses singular {@code /replay} because that is the endpoint required by
     * the assessment prompt. The local slice processes replay synchronously and stores the resulting
     * delivery attempts for later inspection through the detail endpoint.
     */
    @PostMapping("/notification_events/{notification_event_id}/replay")
    @ResponseStatus(HttpStatus.ACCEPTED)
    ApiResponses.ReplayResponse replay(
            @PathVariable("notification_event_id") String notificationEventId,
            Authentication authentication,
            HttpServletRequest request) {
        String correlationId = CorrelationIdFilter.current(request);
        ReplayResult result = service.replay(currentClient(authentication), notificationEventId, correlationId);
        return new ApiResponses.ReplayResponse(
                result.notificationEventId(),
                result.replayStatus(),
                result.message(),
                correlationId);
    }

    private static NotificationEventQuery parseQuery(
            String createdFrom,
            String createdTo,
            String deliveryStatus,
            int page,
            int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("size must be between 1 and 100");
        }
        Optional<Instant> from = parseInstant("created_from", createdFrom);
        Optional<Instant> to = parseInstant("created_to", createdTo);
        if (from.isPresent() && to.isPresent() && from.get().isAfter(to.get())) {
            throw new InvalidRequestException("created_from must be before or equal to created_to");
        }
        Optional<DeliveryStatus> status = Optional.empty();
        if (deliveryStatus != null && !deliveryStatus.isBlank()) {
            try {
                status = Optional.of(DeliveryStatus.fromWireValue(deliveryStatus));
            } catch (IllegalArgumentException exception) {
                throw new InvalidRequestException("delivery_status is invalid");
            }
        }
        return new NotificationEventQuery(from, to, status, page, size);
    }

    private static Optional<Instant> parseInstant(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException exception) {
            throw new InvalidRequestException(fieldName + " must be an ISO-8601 timestamp");
        }
    }

    private static AuthenticatedClient currentClient(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof ClientPrincipal clientPrincipal) {
            return new AuthenticatedClient(clientPrincipal.clientId(), clientPrincipal.scopes());
        }
        throw new InvalidRequestException("Authenticated client is not available");
    }
}
