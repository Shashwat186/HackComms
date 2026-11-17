package com.shashwat.hackcomms.notification;

import com.shashwat.hackcomms.auth.AuthUser;
import com.shashwat.hackcomms.notification.NotificationService.Inbox;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public Inbox inbox(@AuthenticationPrincipal AuthUser me) {
        return service.inbox(me);
    }

    /** Cheap endpoint the UI polls for the bell badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser me) {
        return Map.of("unread", service.unreadCount(me));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@AuthenticationPrincipal AuthUser me, @PathVariable Long id) {
        service.markRead(me, id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal AuthUser me) {
        service.markAllRead(me);
    }
}
