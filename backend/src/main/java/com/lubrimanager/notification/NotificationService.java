package com.lubrimanager.notification;

import com.lubrimanager.common.error.ApiException;
import java.time.Clock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notifications applicatives (§34). {@link #staffAlert} est appelé <em>dans</em> la
 * transaction du fait métier (stock bas, recharge en attente…) : la notification
 * est validée avec lui.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void staffAlert(NotificationType type, NotificationSeverity severity, String title, String body,
                           String entityRef) {
        Notification notification = new Notification();
        notification.setAudience(NotificationAudience.STAFF);
        notification.setType(type);
        notification.setSeverity(severity);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setEntityRef(entityRef);
        notification.setCreatedAt(clock.instant());
        repository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> feed(Long userId, boolean staff, boolean unreadOnly, Pageable pageable) {
        return repository.feed(userId, staff, unreadOnly, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId, boolean staff) {
        return repository.unreadCount(userId, staff);
    }

    @Transactional
    public Notification markRead(Long id, Long userId, boolean staff) {
        Notification notification = repository.findVisible(id, userId, staff)
                .orElseThrow(() -> ApiException.notFound("Notification %d introuvable".formatted(id)));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
        return notification;
    }

    @Transactional
    public int markAllRead(Long userId, boolean staff) {
        return repository.markAllRead(userId, staff, clock.instant());
    }
}
