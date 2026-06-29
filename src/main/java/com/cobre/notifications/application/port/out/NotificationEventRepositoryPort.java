package com.cobre.notifications.application.port.out;

import com.cobre.notifications.application.model.NotificationEventQuery;
import com.cobre.notifications.domain.NotificationEvent;

import java.util.List;
import java.util.Optional;

public interface NotificationEventRepositoryPort {

    List<NotificationEvent> findByClient(String clientId, NotificationEventQuery query);

    long countByClient(String clientId, NotificationEventQuery query);

    Optional<NotificationEvent> findByClientAndId(String clientId, String notificationEventId);

    NotificationEvent save(NotificationEvent notificationEvent);
}
