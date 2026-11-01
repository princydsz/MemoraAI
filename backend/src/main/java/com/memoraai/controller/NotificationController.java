package com.memoraai.controller;

import com.memoraai.dto.NotificationPageResponse;
import com.memoraai.dto.NotificationResponse;
import com.memoraai.dto.UnreadCountResponse;
import com.memoraai.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<NotificationPageResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.list(userDetails.getUsername(), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.unreadCount(userDetails.getUsername()));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.markRead(notificationId, userDetails.getUsername()));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserDetails userDetails) {
        notificationService.markAllRead(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID notificationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.delete(notificationId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
