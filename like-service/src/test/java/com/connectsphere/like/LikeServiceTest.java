package com.connectsphere.like;

import com.connectsphere.like.entity.Like;
import com.connectsphere.like.exception.BadRequestException;
import com.connectsphere.like.repository.LikeRepository;
import com.connectsphere.like.service.LikeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LikeServiceTest — Unit tests for LikeService.
 * Uses Mockito to mock all dependencies.
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock LikeRepository likeRepository;
    @Mock RestTemplate restTemplate;
    @Mock RabbitTemplate rabbitTemplate;
    @InjectMocks LikeService likeService;

    @BeforeEach
    void setUp() {
        /* Inject @Value fields manually since Spring context is not loaded */
        ReflectionTestUtils.setField(likeService, "postServiceUrl", "http://localhost:8082");
        ReflectionTestUtils.setField(likeService, "frontendUrl", "http://localhost:3000");
    }

    /* ── react() tests ─────────────────────────────────────────────── */

    @Test
    void react_newReaction_savesAndReturns() {
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 10L, Like.TargetType.POST))
            .thenReturn(Optional.empty());
        Like saved = new Like();
        saved.setUserId(1L);
        saved.setTargetId(10L);
        saved.setReactionType(Like.ReactionType.LIKE);
        when(likeRepository.save(any())).thenReturn(saved);

        Like result = likeService.react(1L, 10L, Like.TargetType.POST, Like.ReactionType.LIKE);

        assertNotNull(result);
        assertEquals(Like.ReactionType.LIKE, result.getReactionType());
        verify(likeRepository).save(any(Like.class));
    }

    @Test
    void react_existingReaction_updatesReactionType() {
        Like existing = new Like();
        existing.setUserId(1L);
        existing.setTargetId(10L);
        existing.setReactionType(Like.ReactionType.LIKE);
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 10L, Like.TargetType.POST))
            .thenReturn(Optional.of(existing));
        when(likeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Like result = likeService.react(1L, 10L, Like.TargetType.POST, Like.ReactionType.LOVE);

        assertEquals(Like.ReactionType.LOVE, result.getReactionType());
        /* No new record — just update */
        verify(likeRepository, times(1)).save(existing);
    }

    @Test
    void react_nullUserId_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> likeService.react(null, 10L, Like.TargetType.POST, Like.ReactionType.LIKE));
    }

    @Test
    void react_nullTargetId_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> likeService.react(1L, null, Like.TargetType.POST, Like.ReactionType.LIKE));
    }

    @Test
    void react_nullReactionType_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> likeService.react(1L, 10L, Like.TargetType.POST, null));
    }

    /* ── unreact() tests ───────────────────────────────────────────── */

    @Test
    void unreact_existingReaction_deletesIt() {
        Like like = new Like();
        like.setUserId(1L);
        like.setTargetId(10L);
        like.setTargetType(Like.TargetType.POST);
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 10L, Like.TargetType.POST))
            .thenReturn(Optional.of(like));

        likeService.unreact(1L, 10L, Like.TargetType.POST);

        verify(likeRepository).delete(like);
    }

    @Test
    void unreact_noExistingReaction_doesNothing() {
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 10L, Like.TargetType.POST))
            .thenReturn(Optional.empty());

        likeService.unreact(1L, 10L, Like.TargetType.POST);

        verify(likeRepository, never()).delete(any());
    }

    @Test
    void unreact_nullParams_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> likeService.unreact(null, 10L, Like.TargetType.POST));
    }

    /* ── getReactionSummary() tests ────────────────────────────────── */

    @Test
    void getReactionSummary_returnsCorrectCounts() {
        Like l1 = new Like(); l1.setReactionType(Like.ReactionType.LIKE);
        Like l2 = new Like(); l2.setReactionType(Like.ReactionType.LIKE);
        Like l3 = new Like(); l3.setReactionType(Like.ReactionType.LOVE);
        when(likeRepository.findByTargetIdAndTargetType(10L, Like.TargetType.POST))
            .thenReturn(List.of(l1, l2, l3));

        Map<String, Long> summary = likeService.getReactionSummary(10L, Like.TargetType.POST);

        assertEquals(2L, summary.get("LIKE"));
        assertEquals(1L, summary.get("LOVE"));
        assertNull(summary.get("HAHA"));
    }

    @Test
    void getReactionSummary_noReactions_returnsEmptyMap() {
        when(likeRepository.findByTargetIdAndTargetType(10L, Like.TargetType.POST))
            .thenReturn(List.of());

        Map<String, Long> summary = likeService.getReactionSummary(10L, Like.TargetType.POST);

        assertTrue(summary.isEmpty());
    }

    /* ── getUserReaction() tests ───────────────────────────────────── */

    @Test
    void getUserReaction_found_returnsOptionalWithLike() {
        Like like = new Like();
        like.setReactionType(Like.ReactionType.WOW);
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 10L, Like.TargetType.POST))
            .thenReturn(Optional.of(like));

        Optional<Like> result = likeService.getUserReaction(1L, 10L, Like.TargetType.POST);

        assertTrue(result.isPresent());
        assertEquals(Like.ReactionType.WOW, result.get().getReactionType());
    }

    @Test
    void getUserReaction_notFound_returnsEmptyOptional() {
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 10L, Like.TargetType.POST))
            .thenReturn(Optional.empty());

        Optional<Like> result = likeService.getUserReaction(1L, 10L, Like.TargetType.POST);

        assertFalse(result.isPresent());
    }
}
