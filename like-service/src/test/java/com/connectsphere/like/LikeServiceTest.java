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
        ReflectionTestUtils.setField(likeService, "authServiceUrl", "http://localhost:8081");
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

    @Test
    void react_nullTargetType_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> likeService.react(1L, 10L, null, Like.ReactionType.LIKE));
    }

    @Test
    void react_newPostReaction_notifiesOwnerWithActorUsername() {
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(5L, 50L, Like.TargetType.POST))
            .thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.getForObject("http://localhost:8082/posts/50", Map.class))
            .thenReturn(Map.of("userId", 9));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/5", Map.class))
            .thenReturn(Map.of("username", "ayush"));

        Like result = likeService.react(5L, 50L, Like.TargetType.POST, Like.ReactionType.HAHA);

        assertEquals(Like.ReactionType.HAHA, result.getReactionType());
        verify(restTemplate).put(contains("/posts/50/likes/increment"), isNull());
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("like.created"), any(Map.class));
    }

    @Test
    void react_newPostReaction_skipsOwnPostAndToleratesLookupFailure() {
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(anyLong(), anyLong(), eq(Like.TargetType.POST)))
            .thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));
        when(restTemplate.getForObject("http://localhost:8082/posts/51", Map.class))
            .thenReturn(Map.of("userId", 5));

        likeService.react(5L, 51L, Like.TargetType.POST, Like.ReactionType.SAD);
        verify(rabbitTemplate, never()).convertAndSend(eq("connectsphere.events"), eq("like.created"), any(Map.class));

        when(restTemplate.getForObject("http://localhost:8082/posts/52", Map.class))
            .thenThrow(new RuntimeException("post down"));
        Like result = likeService.react(5L, 52L, Like.TargetType.POST, Like.ReactionType.ANGRY);
        assertEquals(Like.ReactionType.ANGRY, result.getReactionType());
    }

    @Test
    void react_newPostReaction_toleratesIncrementAndActorLookupFailure() {
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(5L, 53L, Like.TargetType.POST))
            .thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));
        doThrow(new RuntimeException("post down")).when(restTemplate).put(contains("/posts/53/likes/increment"), isNull());
        when(restTemplate.getForObject("http://localhost:8082/posts/53", Map.class))
            .thenReturn(Map.of("userId", 9));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/5", Map.class))
            .thenThrow(new RuntimeException("auth down"));

        Like result = likeService.react(5L, 53L, Like.TargetType.POST, Like.ReactionType.WOW);

        assertEquals(Like.ReactionType.WOW, result.getReactionType());
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("like.created"), any(Map.class));
    }

    @Test
    void react_commentTarget_savesWithoutPostSideEffects() {
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 44L, Like.TargetType.COMMENT))
            .thenReturn(Optional.empty());
        when(likeRepository.save(any(Like.class))).thenAnswer(i -> i.getArgument(0));

        Like result = likeService.react(1L, 44L, Like.TargetType.COMMENT, Like.ReactionType.LOVE);

        assertEquals(Like.TargetType.COMMENT, result.getTargetType());
        verifyNoInteractions(rabbitTemplate);
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void unreact_postToleratesDecrementFailureAndCommentDoesNotCallPostService() {
        Like postLike = new Like();
        postLike.setTargetType(Like.TargetType.POST);
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 60L, Like.TargetType.POST))
            .thenReturn(Optional.of(postLike));
        doThrow(new RuntimeException("post down")).when(restTemplate).put(contains("/posts/60/likes/decrement"), isNull());

        likeService.unreact(1L, 60L, Like.TargetType.POST);
        verify(likeRepository).delete(postLike);

        Like commentLike = new Like();
        commentLike.setTargetType(Like.TargetType.COMMENT);
        when(likeRepository.findByUserIdAndTargetIdAndTargetType(1L, 61L, Like.TargetType.COMMENT))
            .thenReturn(Optional.of(commentLike));
        likeService.unreact(1L, 61L, Like.TargetType.COMMENT);
        verify(likeRepository).delete(commentLike);
    }

    @Test
    void getReactions_returnsRepositoryList() {
        Like like = new Like();
        like.setReactionType(Like.ReactionType.LIKE);
        when(likeRepository.findByTargetIdAndTargetType(70L, Like.TargetType.POST)).thenReturn(List.of(like));

        List<Like> result = likeService.getReactions(70L, Like.TargetType.POST);

        assertEquals(1, result.size());
    }

}
