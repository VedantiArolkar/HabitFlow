package com.habitflow.controller;

import com.habitflow.dto.ApiMessage;
import com.habitflow.dto.NotificationResponse;
import com.habitflow.security.CurrentUser;
import com.habitflow.service.NotificationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @RequestParam(defaultValue = "30") int limit) {
        return notificationService.list(currentUser.id(), unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount() {
        return Map.of("count", notificationService.unreadCount(currentUser.id()));
    }

    @PatchMapping("/{id}/read")
    public ApiMessage markRead(@PathVariable Long id) {
        notificationService.markRead(currentUser.id(), id);
        return ApiMessage.ok("Marked as read.");
    }

    @PostMapping("/read-all")
    public Map<String, Object> markAllRead() {
        int updated = notificationService.markAllRead(currentUser.id());
        return Map.of("success", true, "updated", updated, "message", "All notifications marked read.");
    }

    @DeleteMapping("/{id}")
    public ApiMessage delete(@PathVariable Long id) {
        notificationService.delete(currentUser.id(), id);
        return ApiMessage.ok("Notification deleted.");
    }
}
