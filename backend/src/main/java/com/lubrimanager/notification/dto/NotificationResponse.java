package com.lubrimanager.notification.dto;

import com.lubrimanager.notification.Notification;
import com.lubrimanager.notification.NotificationSeverity;
import com.lubrimanager.notification.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String body,
        String entityRef,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getSeverity(), n.getTitle(), n.getBody(),
                n.getEntityRef(), n.isRead(), n.getCreatedAt());
    }
}
