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
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

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

    @Test
    void handleEvent_commentReplyMentionAndFollow_buildExpectedDeepLinks() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(Map.of("email", "user@test.com", "username", "user")));

        notificationService.handleEvent(Map.of("type", "COMMENT", "recipientId", "7", "targetId", "44", "message", "commented"));
        notificationService.handleEvent(Map.of("type", "REPLY", "recipientId", "7", "targetId", "45", "message", "replied"));
        notificationService.handleEvent(Map.of("type", "MENTION", "recipientId", "7", "targetId", "46", "message", "mentioned"));
        notificationService.handleEvent(Map.of("type", "FOLLOW", "recipientId", "7", "actorId", "3", "message", "followed"));
        notificationService.handleEvent(Map.of("type", "FOLLOW_REQUEST", "recipientId", "7", "message", "requested"));
        notificationService.handleEvent(Map.of("type", "SYSTEM", "recipientId", "7", "message", "system"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(6)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        assertEquals("http://localhost:3000/post/44", saved.get(0).getDeepLink());
        assertEquals("http://localhost:3000/post/45", saved.get(1).getDeepLink());
        assertEquals("http://localhost:3000/post/46", saved.get(2).getDeepLink());
        assertEquals("http://localhost:3000/profile/7", saved.get(3).getDeepLink());
        assertEquals("http://localhost:3000/notifications", saved.get(4).getDeepLink());
        assertEquals("http://localhost:3000/", saved.get(5).getDeepLink());
    }

    @Test
    void handleEvent_followMilestone_sendsEmail() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Notification follow = new Notification();
        follow.setType("FOLLOW");
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(8L))
            .thenReturn(List.of(follow, follow, follow, follow, follow, follow, follow, follow, follow, follow));
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(Map.of("email", "milestone@test.com", "username", "mila")));

        notificationService.handleEvent(Map.of("type", "FOLLOW", "recipientId", "8", "message", "followed"));

        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertEquals("milestone@test.com", mail.getValue().getTo()[0]);
        assertTrue(mail.getValue().getSubject().contains("10 followers"));
    }

    @Test
    void handleEvent_followMilestone_fetchUserFailureAndEmailFailure_areSwallowed() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("auth down"));
        assertDoesNotThrow(() -> notificationService.handleEvent(Map.of("type", "FOLLOW", "recipientId", "9", "message", "followed")));

        reset(restTemplate, notificationRepository, mailSender);
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Notification follow = new Notification();
        follow.setType("FOLLOW");
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(10L))
            .thenReturn(List.of(follow, follow, follow, follow, follow, follow, follow, follow, follow, follow));
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(Map.of("email", "fail@test.com", "username", "fail")));
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> notificationService.handleEvent(Map.of("type", "FOLLOW", "recipientId", "10", "message", "followed")));
    }

    @Test
    void subscribe_thenCreateNotification_emitsWithoutBreaking() {
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertNotNull(notificationService.subscribe(21L));
        Notification saved = notificationService.createNotification(21L, "GLOBAL", "hello", 1L, null, null);

        assertEquals(21L, saved.getRecipientId());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void sendEmailNotification_successAndFailure_doNotThrow() {
        assertDoesNotThrow(() -> notificationService.sendEmailNotification("to@test.com", "Subject", "Body"));
        verify(mailSender).send(any(SimpleMailMessage.class));

        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        assertDoesNotThrow(() -> notificationService.sendEmailNotification("to@test.com", "Subject", "Body"));
    }

    @Test
    void sendGlobalNotification_rejectsBlankMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> notificationService.sendGlobalNotification("   ", 1L));
        assertEquals("Broadcast message is required.", ex.getMessage());
    }

    @Test
    void sendGlobalNotification_createsForEligibleUsersOnly() {
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(List.of(
                Map.of("userId", 1L, "role", "ADMIN", "active", true),
                Map.of("userId", 2L, "role", "USER", "active", true),
                Map.of("userId", 3L, "role", "GUEST", "active", true),
                Map.of("userId", 4L, "role", "USER", "active", false),
                Map.of("userId", 5L, "role", "USER", "active", true)
            )));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = notificationService.sendGlobalNotification("Platform update", 1L);

        assertEquals(2, result.get("created"));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertEquals(List.of(2L, 5L), captor.getAllValues().stream().map(Notification::getRecipientId).toList());
    }

    @Test
    void sendGlobalNotification_skipsBadUserRowsAndHandlesNullUsers() {
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(List.of(
                Map.of("userId", "bad", "role", "USER"),
                Map.of("userId", 12L, "role", "USER")
            )));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = notificationService.sendGlobalNotification("Hello", 99L);
        assertEquals(1, result.get("created"));

        reset(restTemplate, notificationRepository);
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenReturn(ResponseEntity.ok(null));
        Map<String, Object> nullResult = notificationService.sendGlobalNotification("Hello", 99L);
        assertEquals(0, nullResult.get("created"));
    }

    @Test
    void sendGlobalNotification_authFailureFallsBackToKnownRecipients() {
        Notification n1 = new Notification(); n1.setRecipientId(100L);
        Notification n2 = new Notification(); n2.setRecipientId(101L);
        Notification n3 = new Notification(); n3.setRecipientId(100L);
        when(restTemplate.exchange(anyString(), any(), any(), any(org.springframework.core.ParameterizedTypeReference.class)))
            .thenThrow(new RuntimeException("auth down"));
        when(notificationRepository.findAll()).thenReturn(List.of(n1, n2, n3));
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> result = notificationService.sendGlobalNotification("Fallback", 101L);

        assertEquals(1, result.get("created"));
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertEquals(100L, captor.getValue().getRecipientId());
    }

}
