package com.connectsphere.follow.service;

import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.entity.FollowRequest;
import com.connectsphere.follow.entity.FollowRequest.Status;
import com.connectsphere.follow.repository.FollowRepository;
import com.connectsphere.follow.repository.FollowRequestRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final FollowRequestRepository followRequestRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url}")
    private String authServiceUrl;

    public Map<String, Object> follow(Long followerId, Long followingId) {
        validatePair(followerId, followingId);

        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            return Map.of("status", "FOLLOWING", "following", true, "requested", false);
        }

        if (isPrivateAccount(followingId)) {
            FollowRequest request = followRequestRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                    .orElseGet(() -> {
                        FollowRequest next = new FollowRequest();
                        next.setFollowerId(followerId);
                        next.setFollowingId(followingId);
                        return next;
                    });
            if (request.getStatus() != Status.PENDING) {
                request.setStatus(Status.PENDING);
                request.setUpdatedAt(LocalDateTime.now());
            }
            FollowRequest saved = followRequestRepository.save(request);
            sendFollowRequestNotification(followerId, followingId);
            return Map.of("status", "REQUESTED", "following", false, "requested", true, "requestId", saved.getRequestId());
        }

        createFollowDirect(followerId, followingId);
        sendFollowNotification(followerId, followingId);
        return Map.of("status", "FOLLOWING", "following", true, "requested", false);
    }

    public void unfollow(Long followerId, Long followingId) {
        log.info("User {} unfollowing user {}", followerId, followingId);
        followRepository.deleteAllByPair(followerId, followingId);
        followRequestRepository.findByFollowerIdAndFollowingId(followerId, followingId).ifPresent(followRequestRepository::delete);
    }

    public List<Long> getFollowing(Long userId) { return followRepository.findDistinctFollowingIds(userId); }
    public List<Long> getFollowers(Long userId) { return followRepository.findDistinctFollowerIds(userId); }
    public boolean isFollowing(Long followerId, Long followingId) { return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId); }

    public Map<String, Object> getRelationshipStatus(Long followerId, Long followingId) {
        if (isFollowing(followerId, followingId)) return Map.of("status", "FOLLOWING", "following", true, "requested", false);
        boolean pending = followRequestRepository.existsByFollowerIdAndFollowingIdAndStatus(followerId, followingId, Status.PENDING);
        return Map.of("status", pending ? "REQUESTED" : "NONE", "following", false, "requested", pending);
    }

    public List<FollowRequest> getReceivedRequests(Long userId) {
        return followRequestRepository.findByFollowingIdAndStatusOrderByCreatedAtDesc(userId, Status.PENDING);
    }

    public List<FollowRequest> getSentRequests(Long userId) {
        return followRequestRepository.findByFollowerIdAndStatusOrderByCreatedAtDesc(userId, Status.PENDING);
    }

    public Map<String, Object> approveRequest(Long ownerId, Long requestId) {
        FollowRequest request = followRequestRepository.findById(requestId).orElseThrow(() -> new RuntimeException("Follow request not found."));
        if (!request.getFollowingId().equals(ownerId)) throw new RuntimeException("You can approve only requests sent to your account.");
        request.setStatus(Status.APPROVED);
        request.setUpdatedAt(LocalDateTime.now());
        followRequestRepository.save(request);
        createFollowDirect(request.getFollowerId(), request.getFollowingId());
        sendFollowNotification(request.getFollowerId(), request.getFollowingId());
        return Map.of("status", "APPROVED", "followerId", request.getFollowerId(), "followingId", request.getFollowingId());
    }

    public Map<String, Object> rejectRequest(Long ownerId, Long requestId) {
        FollowRequest request = followRequestRepository.findById(requestId).orElseThrow(() -> new RuntimeException("Follow request not found."));
        if (!request.getFollowingId().equals(ownerId)) throw new RuntimeException("You can reject only requests sent to your account.");
        request.setStatus(Status.REJECTED);
        request.setUpdatedAt(LocalDateTime.now());
        followRequestRepository.save(request);
        return Map.of("status", "REJECTED", "requestId", requestId);
    }

    public boolean isMutual(Long userA, Long userB) {
        return followRepository.existsByFollowerIdAndFollowingId(userA, userB) && followRepository.existsByFollowerIdAndFollowingId(userB, userA);
    }

    public List<Long> getMutualFollowers(Long userId, Long otherUserId) {
        List<Long> myFollowing = getFollowing(userId);
        List<Long> theirFollowing = getFollowing(otherUserId);
        return myFollowing.stream().filter(theirFollowing::contains).collect(Collectors.toList());
    }

    public List<Long> getSuggestedUsers(Long userId) {
        List<Long> alreadyFollowing = getFollowing(userId);
        return alreadyFollowing.stream()
            .flatMap(followingId -> getFollowing(followingId).stream())
            .filter(id -> !id.equals(userId) && !alreadyFollowing.contains(id))
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }

    public Map<String, Long> getCounts(Long userId) {
        return Map.of("following", followRepository.countDistinctFollowing(userId), "followers", followRepository.countDistinctFollowers(userId));
    }

    private void validatePair(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) throw new RuntimeException("followerId and followingId are required.");
        if (followerId.equals(followingId)) throw new RuntimeException("You cannot follow yourself.");
    }

    private Follow createFollowDirect(Long followerId, Long followingId) {
        validatePair(followerId, followingId);
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            return followRepository.findByFollowerIdAndFollowingId(followerId, followingId).orElseGet(() -> {
                Follow existing = new Follow();
                existing.setFollowerId(followerId);
                existing.setFollowingId(followingId);
                return existing;
            });
        }
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        Follow saved = followRepository.save(follow);
        log.info("Follow saved: follower={} following={}", followerId, followingId);
        return saved;
    }

    private boolean isPrivateAccount(Long userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> user = restTemplate.getForObject(authServiceUrl + "/auth/user/" + userId, Map.class);
            Object value = user != null ? user.get("privateAccount") : null;
            return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
        } catch (Exception e) {
            log.warn("Could not check private account for {}: {}", userId, e.getMessage());
            return false;
        }
    }

    private String getUsername(Long userId) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> u = restTemplate.getForObject(authServiceUrl + "/auth/user/" + userId, Map.class);
            if (u != null && u.get("username") != null) return String.valueOf(u.get("username"));
        } catch (Exception ignored) {}
        return String.valueOf(userId);
    }

    private void sendFollowRequestNotification(Long followerId, Long followingId) {
        try {
            rabbitTemplate.convertAndSend("connectsphere.events", "follow.requested", Map.of(
                    "recipientId", followingId,
                    "type", "FOLLOW_REQUEST",
                    "message", getUsername(followerId) + " requested to follow you",
                    "actorId", followerId
            ));
        } catch (Exception e) {
            log.warn("Failed to send follow request notification: {}", e.getMessage());
        }
    }

    private void sendFollowNotification(Long followerId, Long followingId) {
        try {
            rabbitTemplate.convertAndSend("connectsphere.events", "follow.created", Map.of(
                    "recipientId", followingId,
                    "type", "FOLLOW",
                    "message", getUsername(followerId) + " started following you",
                    "actorId", followerId
            ));
        } catch (Exception e) {
            log.warn("Failed to send follow notification: {}", e.getMessage());
        }
    }
}
