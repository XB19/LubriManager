package com.lubrimanager.notification;

import com.lubrimanager.common.web.PageResponse;
import com.lubrimanager.notification.dto.NotificationResponse;
import com.lubrimanager.security.AppUserDetails;
import com.lubrimanager.user.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Mes notifications")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @AuthenticationPrincipal AppUserDetails current) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<Notification> result = service.feed(current.getId(), isStaff(current), unreadOnly,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageResponse<>(result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                result.isLast());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Nombre de notifications non lues (badge)")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AppUserDetails current) {
        return Map.of("unread", service.unreadCount(current.getId(), isStaff(current)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marquer une notification comme lue")
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AppUserDetails current) {
        return NotificationResponse.from(service.markRead(id, current.getId(), isStaff(current)));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Tout marquer comme lu")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AppUserDetails current) {
        return Map.of("updated", service.markAllRead(current.getId(), isStaff(current)));
    }

    private static boolean isStaff(AppUserDetails current) {
        return current.getRole() == Role.ADMIN || current.getRole() == Role.MANAGER;
    }
}
