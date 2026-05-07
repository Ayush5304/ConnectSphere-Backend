package com.connectsphere.notification.service;

import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NotificationService â€” Notification business logic.
 *
 * Responsibilities:
 *   1. Consumes RabbitMQ events and persists notifications to DB.
 *   2. Sends milestone congratulation emails on FOLLOW events.
 *   3. Provides API for reading, marking, and deleting notifications.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> liveEmitters = new ConcurrentHashMap<>();

    @Value("${auth.service.url}")
    private String authServiceUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${service.internal.token:internal-service-token}")
    private String internalToken;

    /** Follower counts that trigger congratulation emails. */
    private static final List<Long> MILESTONES = List.of(10L, 50L, 100L, 500L, 1000L);

    /**
     * handleEvent() â€” RabbitMQ consumer for notification.queue.
     * Builds and saves a Notification from the incoming event map.
     * Sends milestone email for FOLLOW events.
     */
    @RabbitListener(queues = "notification.queue")
    public void handleEvent(Map<String, Object> event) {
        try {
            String type = (String) event.getOrDefault("type", "SYSTEM");
            Long recipientId = Long.parseLong(event.get("recipientId").toString());
            String message = (String) event.getOrDefault("message", "You have a new notification");

            log.info("Processing {} notification for recipientId={}", type, recipientId);

            Notification n = new Notification();
            n.setRecipientId(recipientId);
            n.setType(type);
            n.setMessage(message);

            if (event.containsKey("actorId"))
                n.setActorId(Long.parseLong(event.get("actorId").toString()));

            Long targetId = null;
            if (event.containsKey("targetId")) {
                targetId = Long.parseLong(event.get("targetId").toString());
                n.setTargetId(targetId);
            }

            n.setDeepLink(buildDeepLink(type, targetId, recipientId));
            Notification saved = notificationRepository.save(n);
            emitToUser(recipientId, saved);
            log.info("Notification saved for recipientId={} type={}", recipientId, type);

            if ("FOLLOW".equals(type)) {
                handleFollowEmail(recipientId, message);
            }
        } catch (Exception e) {
            log.error("Failed to process notification event: {}", e.getMessage());
        }
    }

    /**
     * buildDeepLink() â€” Builds the frontend URL for the notification click action.
     * LIKE/COMMENT/REPLY/MENTION â†’ post page; FOLLOW â†’ profile page; default â†’ home.
     */
    private String buildDeepLink(String type, Long targetId, Long recipientId) {
        return switch (type) {
            case "LIKE", "COMMENT", "REPLY", "MENTION" ->
                targetId != null ? frontendUrl + "/post/" + targetId : frontendUrl + "/";
            case "FOLLOW" -> frontendUrl + "/profile/" + recipientId;
            default -> frontendUrl + "/";
        };
    }

    /**
     * handleFollowEmail() â€” Sends a congratulation email when a follower milestone is reached.
     * Uses FOLLOW notification count as a proxy for follower count.
     */
    private void handleFollowEmail(Long recipientId, String message) {
        try {
            Map<String, Object> user = fetchUser(recipientId);
            if (user == null) return;
            String email = (String) user.get("email");
            String username = (String) user.get("username");
            if (email == null) return;

            long followNotifCount = notificationRepository
                    .findByRecipientIdOrderByCreatedAtDesc(recipientId)
                    .stream().filter(n -> "FOLLOW".equals(n.getType())).count();

            if (MILESTONES.contains(followNotifCount)) {
                log.info("Sending milestone email to {} for {} followers", email, followNotifCount);
                sendEmailNotification(email,
                        "ðŸŽ‰ You reached " + followNotifCount + " followers on ConnectSphere!",
                        "Hi " + username + ",\n\nCongratulations! You now have " + followNotifCount
                                + " followers on ConnectSphere.\n\nKeep sharing great content!\n\nConnectSphere Team");
            }
        } catch (Exception e) {
            log.warn("Failed to handle follow email for recipientId={}: {}", recipientId, e.getMessage());
        }
    }

    /** fetchUser() â€” Calls auth-service with internal token to get user details. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchUser(Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + internalToken);
            return restTemplate.exchange(
                    authServiceUrl + "/auth/user/" + userId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            ).getBody();
        } catch (Exception e) {
            log.warn("Failed to fetch user id={}: {}", userId, e.getMessage());
            return null;
        }
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        liveEmitters.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("notification:ready").data(Map.of("userId", userId)));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }
        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = liveEmitters.get(userId);
        if (emitters == null) return;
        emitters.remove(emitter);
        if (emitters.isEmpty()) liveEmitters.remove(userId);
    }

    private void emitToUser(Long userId, Notification notification) {
        CopyOnWriteArrayList<SseEmitter> emitters = liveEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification:new").data(notification));
            } catch (IOException | IllegalStateException e) {
                removeEmitter(userId, emitter);
            }
        }
    }
    /** getForUser() â€” Returns all notifications for a user, newest first. */
    public List<Notification> getForUser(Long userId) {
        log.debug("Fetching notifications for userId={}", userId);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    /** markRead() â€” Marks a single notification as read. */
    public void markRead(Long notificationId) {
        log.debug("Marking notification id={} as read", notificationId);
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    /** countUnread() â€” Returns count of unread notifications for a user. */
    public long countUnread(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    /** markAllRead() â€” Marks all notifications as read for a user in one query. */
    public void markAllRead(Long userId) {
        log.info("Marking all notifications read for userId={}", userId);
        notificationRepository.markAllReadByRecipientId(userId);
    }

    /** delete() â€” Permanently deletes a notification. */
    public void delete(Long notificationId) {
        log.info("Deleting notification id={}", notificationId);
        notificationRepository.deleteById(notificationId);
    }

    /** createNotification() â€” Creates a notification directly (non-RabbitMQ path). */
    public Notification createNotification(Long recipientId, String type, String message,
                                            Long actorId, Long targetId, String deepLink) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setMessage(message);
        n.setActorId(actorId);
        n.setTargetId(targetId);
        n.setDeepLink(deepLink != null ? deepLink : buildDeepLink(type, targetId, recipientId));
        log.info("Creating direct notification for recipientId={} type={}", recipientId, type);
        Notification saved = notificationRepository.save(n);
        emitToUser(recipientId, saved);
        return saved;
    }

    /** sendEmailNotification() â€” Sends a plain-text email. Failure is logged, not thrown. */
    public void sendEmailNotification(String toEmail, String subject, String body) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(toEmail);
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
            log.info("Email sent to {}", toEmail);
        } catch (Exception e) {
            log.warn("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * sendGlobalNotification() â€” Sends a notification to ALL users (admin feature).
     * Falls back to notifying known users from existing notifications if auth-service is down.
     */
    public Map<String, Object> sendGlobalNotification(String message, Long adminId) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Broadcast message is required.");
        }
        log.info("Sending global notification: {}", message);
        int created = 0;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + internalToken);
            List<Map<String, Object>> users = restTemplate.exchange(
                    authServiceUrl + "/auth/admin/users",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            ).getBody();

            if (users != null) {
                for (Map<String, Object> u : users) {
                    try {
                        Long recipientId = Long.parseLong(u.get("userId").toString());
                        String role = String.valueOf(u.getOrDefault("role", "USER"));
                        Boolean active = u.containsKey("active") ? Boolean.valueOf(String.valueOf(u.get("active"))) : Boolean.TRUE;
                        if ((adminId != null && adminId.equals(recipientId)) || "ADMIN".equalsIgnoreCase(role) || "GUEST".equalsIgnoreCase(role) || !active) {
                            continue;
                        }
                        Notification saved = createNotification(recipientId, "GLOBAL", message, adminId, null, frontendUrl + "/");
                        if (saved != null) created++;
                    } catch (Exception e) {
                        log.warn("Failed to create global notification for user: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Auth-service unavailable for global notification, using fallback: {}", e.getMessage());
            for (Long recipientId : notificationRepository.findAll().stream().map(Notification::getRecipientId).distinct().toList()) {
                if (adminId != null && adminId.equals(recipientId)) continue;
                Notification saved = createNotification(recipientId, "GLOBAL", message, adminId, null, frontendUrl + "/");
                if (saved != null) created++;
            }
        }
        return Map.of("message", "Broadcast sent.", "created", created);
    }
}

