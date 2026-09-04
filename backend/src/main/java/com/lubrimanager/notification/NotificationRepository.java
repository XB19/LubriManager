package com.lubrimanager.notification;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select n from Notification n
            where (n.userId = :userId or (n.audience = com.lubrimanager.notification.NotificationAudience.STAFF and :staff = true))
              and (:unreadOnly = false or n.readAt is null)
            """)
    Page<Notification> feed(@Param("userId") Long userId, @Param("staff") boolean staff,
                            @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where (n.userId = :userId or (n.audience = com.lubrimanager.notification.NotificationAudience.STAFF and :staff = true))
              and n.readAt is null
            """)
    long unreadCount(@Param("userId") Long userId, @Param("staff") boolean staff);

    @Query("""
            select n from Notification n
            where n.id = :id
              and (n.userId = :userId or (n.audience = com.lubrimanager.notification.NotificationAudience.STAFF and :staff = true))
            """)
    Optional<Notification> findVisible(@Param("id") Long id, @Param("userId") Long userId, @Param("staff") boolean staff);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.readAt is null
              and (n.userId = :userId or (n.audience = com.lubrimanager.notification.NotificationAudience.STAFF and :staff = true))
            """)
    int markAllRead(@Param("userId") Long userId, @Param("staff") boolean staff, @Param("now") Instant now);
}
