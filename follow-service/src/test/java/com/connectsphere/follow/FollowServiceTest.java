package com.connectsphere.follow;

import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowRequest;
import com.connectsphere.follow.entity.FollowRequest.Status;
import com.connectsphere.follow.repository.FollowRepository;
import com.connectsphere.follow.repository.FollowRequestRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock FollowRepository followRepository;
    @Mock FollowRequestRepository followRequestRepository;
    @Mock RabbitTemplate rabbitTemplate;
    @Mock RestTemplate restTemplate;
    @InjectMocks FollowService followService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(followService, "authServiceUrl", "http://localhost:8081");
    }

    @Test
    void follow_publicAccount_createsFollowAndReturnsFollowingStatus() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(restTemplate.getForObject("http://localhost:8081/auth/user/2", Map.class))
            .thenReturn(Map.of("privateAccount", false, "username", "target"));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenReturn(Map.of("username", "ayush"));
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> {
            Follow follow = invocation.getArgument(0);
            follow.setFollowId(10L);
            return follow;
        });

        Map<String, Object> result = followService.follow(1L, 2L);

        assertEquals("FOLLOWING", result.get("status"));
        assertEquals(true, result.get("following"));
        assertEquals(false, result.get("requested"));
        verify(followRepository).save(any(Follow.class));
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("follow.created"), any(Map.class));
    }

    @Test
    void follow_privateAccount_createsPendingRequestInsteadOfFollow() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(restTemplate.getForObject("http://localhost:8081/auth/user/2", Map.class))
            .thenReturn(Map.of("privateAccount", true, "username", "privateUser"));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenReturn(Map.of("username", "ayush"));
        when(followRequestRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(followRequestRepository.save(any(FollowRequest.class))).thenAnswer(invocation -> {
            FollowRequest request = invocation.getArgument(0);
            request.setRequestId(55L);
            return request;
        });

        Map<String, Object> result = followService.follow(1L, 2L);

        assertEquals("REQUESTED", result.get("status"));
        assertEquals(false, result.get("following"));
        assertEquals(true, result.get("requested"));
        assertEquals(55L, result.get("requestId"));
        verify(followRepository, never()).save(any(Follow.class));
        verify(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("follow.requested"), any(Map.class));
    }

    @Test
    void follow_alreadyFollowing_returnsFollowingStatusWithoutSaving() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        Map<String, Object> result = followService.follow(1L, 2L);

        assertEquals("FOLLOWING", result.get("status"));
        assertEquals(true, result.get("following"));
        verify(followRepository, never()).save(any());
        verifyNoInteractions(followRequestRepository);
    }

    @Test
    void follow_selfFollow_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> followService.follow(1L, 1L));
    }

    @Test
    void follow_nullFollowerId_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> followService.follow(null, 2L));
    }

    @Test
    void unfollow_removesFollowAndPendingRequestIfPresent() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(7L);
        request.setFollowerId(1L);
        request.setFollowingId(2L);
        when(followRequestRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(request));

        followService.unfollow(1L, 2L);

        verify(followRepository).deleteAllByPair(1L, 2L);
        verify(followRequestRepository).delete(request);
    }

    @Test
    void unfollow_withoutPendingRequestOnlyDeletesFollowPair() {
        when(followRequestRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());

        followService.unfollow(1L, 2L);

        verify(followRepository).deleteAllByPair(1L, 2L);
        verify(followRequestRepository, never()).delete(any());
    }

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

    @Test
    void getFollowing_returnsDistinctFollowingIds() {
        when(followRepository.findDistinctFollowingIds(1L)).thenReturn(List.of(2L, 3L));

        List<Long> result = followService.getFollowing(1L);

        assertEquals(List.of(2L, 3L), result);
    }

    @Test
    void getFollowers_returnsDistinctFollowerIds() {
        when(followRepository.findDistinctFollowerIds(2L)).thenReturn(List.of(1L, 3L));

        List<Long> result = followService.getFollowers(2L);

        assertEquals(List.of(1L, 3L), result);
    }

    @Test
    void getCounts_returnsDistinctCounts() {
        when(followRepository.countDistinctFollowing(1L)).thenReturn(2L);
        when(followRepository.countDistinctFollowers(1L)).thenReturn(4L);

        Map<String, Long> counts = followService.getCounts(1L);

        assertEquals(2L, counts.get("following"));
        assertEquals(4L, counts.get("followers"));
    }

    @Test
    void relationshipStatus_returnsRequestedWhenPendingRequestExists() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(followRequestRepository.existsByFollowerIdAndFollowingIdAndStatus(1L, 2L, Status.PENDING))
            .thenReturn(true);

        Map<String, Object> result = followService.getRelationshipStatus(1L, 2L);

        assertEquals("REQUESTED", result.get("status"));
        assertEquals(false, result.get("following"));
        assertEquals(true, result.get("requested"));
    }

    @Test
    void approveRequest_createsFollowAndReturnsApprovedStatus() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(9L);
        request.setFollowerId(1L);
        request.setFollowingId(2L);
        request.setStatus(Status.PENDING);
        when(followRequestRepository.findById(9L)).thenReturn(Optional.of(request));
        when(followRequestRepository.save(any(FollowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenReturn(Map.of("username", "ayush"));

        Map<String, Object> result = followService.approveRequest(2L, 9L);

        assertEquals("APPROVED", result.get("status"));
        assertEquals(Status.APPROVED, request.getStatus());
        verify(followRepository).save(any(Follow.class));
    }

    @Test
    void approveRequest_existingFollowDoesNotSaveDuplicate() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(9L);
        request.setFollowerId(1L);
        request.setFollowingId(2L);
        request.setStatus(Status.PENDING);
        Follow existing = new Follow();
        existing.setFollowId(12L);
        existing.setFollowerId(1L);
        existing.setFollowingId(2L);
        when(followRequestRepository.findById(9L)).thenReturn(Optional.of(request));
        when(followRequestRepository.save(any(FollowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);
        when(followRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(existing));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenReturn(Map.of("username", "ayush"));

        Map<String, Object> result = followService.approveRequest(2L, 9L);

        assertEquals("APPROVED", result.get("status"));
        verify(followRepository, never()).save(any(Follow.class));
    }

    @Test
    void approveRequest_missingRequestThrowsRuntimeException() {
        when(followRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> followService.approveRequest(2L, 99L));
    }

    @Test
    void approveRequest_rejectsWrongOwner() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(9L);
        request.setFollowerId(1L);
        request.setFollowingId(2L);
        when(followRequestRepository.findById(9L)).thenReturn(Optional.of(request));

        assertThrows(RuntimeException.class, () -> followService.approveRequest(99L, 9L));

        verify(followRepository, never()).save(any());
    }

    @Test
    void rejectRequest_updatesStatus() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(9L);
        request.setFollowerId(1L);
        request.setFollowingId(2L);
        request.setStatus(Status.PENDING);
        when(followRequestRepository.findById(9L)).thenReturn(Optional.of(request));
        when(followRequestRepository.save(any(FollowRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = followService.rejectRequest(2L, 9L);

        assertEquals("REJECTED", result.get("status"));
        assertEquals(Status.REJECTED, request.getStatus());
    }

    @Test
    void rejectRequest_rejectsWrongOwner() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(9L);
        request.setFollowerId(1L);
        request.setFollowingId(2L);
        when(followRequestRepository.findById(9L)).thenReturn(Optional.of(request));

        assertThrows(RuntimeException.class, () -> followService.rejectRequest(99L, 9L));

        verify(followRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequest_missingRequestThrowsRuntimeException() {
        when(followRequestRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> followService.rejectRequest(2L, 99L));
    }

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

    @Test
    void getSuggestedUsers_returnsFriendsOfFriendsExcludingSelfAndExistingFollows() {
        when(followRepository.findDistinctFollowingIds(1L)).thenReturn(List.of(2L, 3L));
        when(followRepository.findDistinctFollowingIds(2L)).thenReturn(List.of(1L, 4L, 5L));
        when(followRepository.findDistinctFollowingIds(3L)).thenReturn(List.of(5L, 6L));

        List<Long> suggestions = followService.getSuggestedUsers(1L);

        assertEquals(List.of(4L, 5L, 6L), suggestions);
    }

    @Test
    void relationshipStatus_returnsFollowingWhenAlreadyFollowing() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        Map<String, Object> result = followService.getRelationshipStatus(1L, 2L);

        assertEquals("FOLLOWING", result.get("status"));
        assertEquals(true, result.get("following"));
        assertEquals(false, result.get("requested"));
    }

    @Test
    void relationshipStatus_returnsNoneWhenNoFollowOrPendingRequest() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(followRequestRepository.existsByFollowerIdAndFollowingIdAndStatus(1L, 2L, Status.PENDING))
            .thenReturn(false);

        Map<String, Object> result = followService.getRelationshipStatus(1L, 2L);

        assertEquals("NONE", result.get("status"));
        assertEquals(false, result.get("following"));
        assertEquals(false, result.get("requested"));
    }

    @Test
    void getReceivedRequests_returnsPendingRequestsForUser() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(1L);
        when(followRequestRepository.findByFollowingIdAndStatusOrderByCreatedAtDesc(2L, Status.PENDING))
            .thenReturn(List.of(request));

        List<FollowRequest> result = followService.getReceivedRequests(2L);

        assertEquals(List.of(request), result);
    }

    @Test
    void getSentRequests_returnsPendingRequestsFromUser() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(2L);
        when(followRequestRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(1L, Status.PENDING))
            .thenReturn(List.of(request));

        List<FollowRequest> result = followService.getSentRequests(1L);

        assertEquals(List.of(request), result);
    }

    @Test
    void getMutualFollowers_returnsCommonFollowingIds() {
        when(followRepository.findDistinctFollowingIds(1L)).thenReturn(List.of(2L, 3L, 4L));
        when(followRepository.findDistinctFollowingIds(9L)).thenReturn(List.of(3L, 4L, 5L));

        List<Long> result = followService.getMutualFollowers(1L, 9L);

        assertEquals(List.of(3L, 4L), result);
    }

    @Test
    void follow_privateAccount_existingApprovedRequestResetsToPending() {
        FollowRequest request = new FollowRequest();
        request.setRequestId(77L);
        request.setFollowerId(1L);
        request.setFollowingId(2L);
        request.setStatus(Status.APPROVED);
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(restTemplate.getForObject("http://localhost:8081/auth/user/2", Map.class))
            .thenReturn(Map.of("privateAccount", true));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenReturn(Map.of("username", "ayush"));
        when(followRequestRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.of(request));
        when(followRequestRepository.save(request)).thenReturn(request);

        Map<String, Object> result = followService.follow(1L, 2L);

        assertEquals("REQUESTED", result.get("status"));
        assertEquals(Status.PENDING, request.getStatus());
        assertNotNull(request.getUpdatedAt());
    }

    @Test
    void follow_privateAccountStringValueCreatesPendingRequest() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(restTemplate.getForObject("http://localhost:8081/auth/user/2", Map.class))
            .thenReturn(Map.of("privateAccount", "true"));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenReturn(Map.of("username", "ayush"));
        when(followRequestRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(followRequestRepository.save(any(FollowRequest.class))).thenAnswer(invocation -> {
            FollowRequest request = invocation.getArgument(0);
            request.setRequestId(66L);
            return request;
        });

        Map<String, Object> result = followService.follow(1L, 2L);

        assertEquals("REQUESTED", result.get("status"));
        assertEquals(66L, result.get("requestId"));
    }

    @Test
    void follow_authLookupFailureTreatsAccountAsPublicAndNotificationFailureIsIgnored() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(restTemplate.getForObject("http://localhost:8081/auth/user/2", Map.class))
            .thenThrow(new RuntimeException("auth down"));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenThrow(new RuntimeException("auth down"));
        when(followRepository.save(any(Follow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("broker down"))
            .when(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("follow.created"), any(Map.class));

        Map<String, Object> result = followService.follow(1L, 2L);

        assertEquals("FOLLOWING", result.get("status"));
        verify(followRepository).save(any(Follow.class));
    }

    @Test
    void follow_privateRequestNotificationFailureIsIgnored() {
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);
        when(restTemplate.getForObject("http://localhost:8081/auth/user/2", Map.class))
            .thenReturn(Map.of("privateAccount", true));
        when(restTemplate.getForObject("http://localhost:8081/auth/user/1", Map.class))
            .thenReturn(Map.of());
        when(followRequestRepository.findByFollowerIdAndFollowingId(1L, 2L)).thenReturn(Optional.empty());
        when(followRequestRepository.save(any(FollowRequest.class))).thenAnswer(invocation -> {
            FollowRequest request = invocation.getArgument(0);
            request.setRequestId(67L);
            return request;
        });
        doThrow(new RuntimeException("broker down"))
            .when(rabbitTemplate).convertAndSend(eq("connectsphere.events"), eq("follow.requested"), any(Map.class));

        Map<String, Object> result = followService.follow(1L, 2L);

        assertEquals("REQUESTED", result.get("status"));
        assertEquals(67L, result.get("requestId"));
    }

}
