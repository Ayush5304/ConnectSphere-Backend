package com.connectsphere.notification.controller;

import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationResource {

    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<Notification> create(@RequestBody Map<String, Object> body) {
        Long recipientId = Long.parseLong(body.get("recipientId").toString());
        Long actorId = body.containsKey("actorId") ? Long.parseLong(body.get("actorId").toString()) : null;
        Long targetId = body.containsKey("targetId") ? Long.parseLong(body.get("targetId").toString()) : null;
        return ResponseEntity.ok(notificationService.createNotification(
            recipientId, (String) body.get("type"), (String) body.get("message"),
            actorId, targetId, (String) body.get("deepLink")));
    }

    @PostMapping("/admin/email")
    public ResponseEntity<Void> sendEmail(@RequestBody Map<String, String> body) {
        notificationService.sendEmailNotification(
            body.get("toEmail"), body.get("subject"), body.get("body"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> getForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.getForUser(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/user/{userId}/read-all")
    public ResponseEntity<Void> markAllRead(@PathVariable Long userId) {
        notificationService.markAllRead(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        notificationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(userId)));
    }

    @PostMapping("/admin/global")
    public ResponseEntity<Void> sendGlobal(@RequestBody Map<String, String> body) {
        notificationService.sendGlobalNotification(body.get("message"));
        return ResponseEntity.ok().build();
    }
}
