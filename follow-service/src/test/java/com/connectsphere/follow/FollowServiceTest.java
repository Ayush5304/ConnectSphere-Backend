package com.connectsphere.follow;

import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.repository.FollowRepository;
import com.connectsphere.follow.service.FollowService;
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
 * FollowServiceTest — Unit tests for FollowService.
 * Uses Mockito to mock all dependencies.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock FollowRepository followRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock RestTemplate restTemplate;
    @InjectMocks FollowService followService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(followService, "authServiceUrl", "http://localhost:8081");
    }

    /* ── follow() tests ────────────────────────────────────────────── */

    @Test
    void follow_success_savesAndReturns() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        Follow saved = new Follow();
        saved.setFollowerId(1L);
        saved.setFollowingId(2L);
        when(followRepository.save(any())).thenReturn(saved);

        Follow result = followService.follow(1L, 2L);

        assertNotNull(result);
        assertEquals(1L, result.getFollowerId());
        assertEquals(2L, result.getFollowingId());
        verify(followRepository).save(any(Follow.class));
    }

    @Test
    void follow_alreadyFollowing_throwsRuntimeException() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);
        assertThrows(RuntimeException.class, () -> followService.follow(1L, 2L));
    }

    @Test
    void follow_selfFollow_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> followService.follow(1L, 1L));
    }

    @Test
    void follow_nullFollowerId_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> followService.follow(null, 2L));
    }

    /* ── unfollow() tests ──────────────────────────────────────────── */

    @Test
    void unfollow_existingFollow_deletesIt() {
        Follow follow = new Follow();
        follow.setFollowerId(1L);
        follow.setFollowingId(2L);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L))
            .thenReturn(Optional.of(follow));

        followService.unfollow(1L, 2L);

        verify(followRepository).delete(follow);
    }

    @Test
    void unfollow_notFollowing_doesNothing() {
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L))
            .thenReturn(Optional.empty());

        followService.unfollow(1L, 2L);

        verify(followRepository, never()).delete(any());
    }

    /* ── isFollowing() tests ───────────────────────────────────────── */

    @Test
    void isFollowing_returnsTrue_whenFollowExists() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);
        assertTrue(followService.isFollowing(1L, 2L));
    }

    @Test
    void isFollowing_returnsFalse_whenFollowDoesNotExist() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        assertFalse(followService.isFollowing(1L, 2L));
    }

    /* ── getFollowing() tests ──────────────────────────────────────── */

    @Test
    void getFollowing_returnsListOfFollowingIds() {
        Follow f1 = new Follow(); f1.setFollowingId(2L);
        Follow f2 = new Follow(); f2.setFollowingId(3L);
        when(followRepository.findByFollowerId(1L)).thenReturn(List.of(f1, f2));

        List<Long> result = followService.getFollowing(1L);

        assertEquals(2, result.size());
        assertTrue(result.contains(2L));
        assertTrue(result.contains(3L));
    }

    /* ── getFollowers() tests ──────────────────────────────────────── */

    @Test
    void getFollowers_returnsListOfFollowerIds() {
        Follow f1 = new Follow(); f1.setFollowerId(3L);
        when(followRepository.findByFollowingId(2L)).thenReturn(List.of(f1));

        List<Long> result = followService.getFollowers(2L);

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0));
    }

    /* ── getCounts() tests ─────────────────────────────────────────── */

    @Test
    void getCounts_returnsFollowingAndFollowerCounts() {
        when(followRepository.countByFollowerId(1L)).thenReturn(5L);
        when(followRepository.countByFollowingId(1L)).thenReturn(10L);

        Map<String, Long> counts = followService.getCounts(1L);

        assertEquals(5L, counts.get("following"));
        assertEquals(10L, counts.get("followers"));
    }

    /* ── isMutual() tests ──────────────────────────────────────────── */

    @Test
    void isMutual_bothFollow_returnsTrue() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingId(2L, 1L)).thenReturn(true);
        assertTrue(followService.isMutual(1L, 2L));
    }

    @Test
    void isMutual_onlyOneFollows_returnsFalse() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFollowingId(2L, 1L)).thenReturn(false);
        assertFalse(followService.isMutual(1L, 2L));
    }
}
