package com.connectsphere.follow.controller;

import com.connectsphere.follow.entity.FollowRequest;
import com.connectsphere.follow.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/follows")
@RequiredArgsConstructor
public class FollowResource {
    private final FollowService followService;

    @PostMapping("/{followerId}/follow/{followingId}")
    public ResponseEntity<Map<String, Object>> follow(@PathVariable Long followerId, @PathVariable Long followingId) {
        return ResponseEntity.ok(followService.follow(followerId, followingId));
    }

    @DeleteMapping("/{followerId}/unfollow/{followingId}")
    public ResponseEntity<Void> unfollow(@PathVariable Long followerId, @PathVariable Long followingId) {
        followService.unfollow(followerId, followingId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/following")
    public ResponseEntity<List<Long>> following(@PathVariable Long userId) { return ResponseEntity.ok(followService.getFollowing(userId)); }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<Long>> followers(@PathVariable Long userId) { return ResponseEntity.ok(followService.getFollowers(userId)); }

    @GetMapping("/{userId}/counts")
    public ResponseEntity<Map<String, Long>> counts(@PathVariable Long userId) { return ResponseEntity.ok(followService.getCounts(userId)); }

    @GetMapping("/{followerId}/is-following/{followingId}")
    public ResponseEntity<Map<String, Boolean>> isFollowing(@PathVariable Long followerId, @PathVariable Long followingId) {
        return ResponseEntity.ok(Map.of("following", followService.isFollowing(followerId, followingId)));
    }

    @GetMapping("/{followerId}/status/{followingId}")
    public ResponseEntity<Map<String, Object>> relationshipStatus(@PathVariable Long followerId, @PathVariable Long followingId) {
        return ResponseEntity.ok(followService.getRelationshipStatus(followerId, followingId));
    }

    @GetMapping("/{userId}/requests/received")
    public ResponseEntity<List<FollowRequest>> receivedRequests(@PathVariable Long userId) { return ResponseEntity.ok(followService.getReceivedRequests(userId)); }

    @GetMapping("/{userId}/requests/sent")
    public ResponseEntity<List<FollowRequest>> sentRequests(@PathVariable Long userId) { return ResponseEntity.ok(followService.getSentRequests(userId)); }

    @PutMapping("/{ownerId}/requests/{requestId}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long ownerId, @PathVariable Long requestId) { return ResponseEntity.ok(followService.approveRequest(ownerId, requestId)); }

    @PutMapping("/{ownerId}/requests/{requestId}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long ownerId, @PathVariable Long requestId) { return ResponseEntity.ok(followService.rejectRequest(ownerId, requestId)); }

    @GetMapping("/{userA}/mutual/{userB}")
    public ResponseEntity<Map<String, Boolean>> mutual(@PathVariable Long userA, @PathVariable Long userB) { return ResponseEntity.ok(Map.of("mutual", followService.isMutual(userA, userB))); }

    @GetMapping("/{userId}/mutual-list/{otherUserId}")
    public ResponseEntity<List<Long>> mutualList(@PathVariable Long userId, @PathVariable Long otherUserId) { return ResponseEntity.ok(followService.getMutualFollowers(userId, otherUserId)); }

    @GetMapping("/{userId}/suggestions")
    public ResponseEntity<List<Long>> suggestions(@PathVariable Long userId) { return ResponseEntity.ok(followService.getSuggestedUsers(userId)); }
}
