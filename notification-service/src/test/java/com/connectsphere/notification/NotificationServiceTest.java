package com.connectsphere.notification;

import com.connectsphere.notification.entity.Notification;
import com.connectsphere.notification.repository.NotificationRepository;
import com.connectsphere.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * NotificationServiceTest — Unit tests for NotificationService.
 * Uses Mockito to mock all dependencies.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock RestTemplate restTemplate;
    @Mock JavaMailSender mailSender;
    @InjectMocks NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "authServiceUrl", "http://localhost:8081");
        ReflectionTestUtils.setField(notificationService, "fromEmail", "test@connectsphere.com");
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(notificationService, "internalToken", "internal-service-token");
    }

    /* ── handleEvent() tests ───────────────────────────────────────── */

    @Test
    void handleEvent_validLikeEvent_savesNotification() {
        java.util.Map<String, Object> event = new java.util.HashMap<>();
        event.put("type", "LIKE");
        event.put("recipientId", "5");
        event.put("message", "Someone liked your post");
        event.put("actorId", "2");
        event.put("targetId", "10");

        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService.handleEvent(event);

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void handleEvent_missingRecipientId_doesNotThrow() {
        java.util.Map<String, Object> event = new java.util.HashMap<>();
        event.put("type", "LIKE");
        /* recipientId intentionally missing */

        assertDoesNotThrow(() -> notificationService.handleEvent(event));
        verify(notificationRepository, never()).save(any());
    }

    /* ── getForUser() tests ────────────────────────────────────────── */

    @Test
    void getForUser_returnsNotificationsForUser() {
        Notification n1 = new Notification(); n1.setRecipientId(1L);
        Notification n2 = new Notification(); n2.setRecipientId(1L);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L))
            .thenReturn(List.of(n1, n2));

        List<Notification> result = notificationService.getForUser(1L);

        assertEquals(2, result.size());
    }

    @Test
    void getForUser_noNotifications_returnsEmptyList() {
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(1L))
            .thenReturn(List.of());

        List<Notification> result = notificationService.getForUser(1L);

        assertTrue(result.isEmpty());
    }

    /* ── markRead() tests ──────────────────────────────────────────── */

    @Test
    void markRead_existingNotification_setsReadTrue() {
        Notification n = new Notification();
        n.setRead(false);
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService.markRead(1L);

        assertTrue(n.isRead());
        verify(notificationRepository).save(n);
    }

    @Test
    void markRead_notFound_doesNothing() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        notificationService.markRead(99L);

        verify(notificationRepository, never()).save(any());
    }

    /* ── countUnread() tests ───────────────────────────────────────── */

    @Test
    void countUnread_returnsCorrectCount() {
        when(notificationRepository.countByRecipientIdAndIsReadFalse(1L)).thenReturn(3L);

        long count = notificationService.countUnread(1L);

        assertEquals(3L, count);
    }

    /* ── delete() tests ────────────────────────────────────────────── */

    @Test
    void delete_callsRepositoryDeleteById() {
        notificationService.delete(1L);
        verify(notificationRepository).deleteById(1L);
    }

    /* ── createNotification() tests ────────────────────────────────── */

    @Test
    void createNotification_savesAndReturns() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Notification result = notificationService.createNotification(
            1L, "LIKE", "Someone liked your post", 2L, 10L, null);

        assertNotNull(result);
        assertEquals(1L, result.getRecipientId());
        assertEquals("LIKE", result.getType());
        verify(notificationRepository).save(any(Notification.class));
    }

    /* ── markAllRead() tests ───────────────────────────────────────── */

    @Test
    void markAllRead_callsRepositoryBulkUpdate() {
        notificationService.markAllRead(1L);
        verify(notificationRepository).markAllReadByRecipientId(1L);
    }
}
