/**
 * FollowResource.java — Follow REST Controller
 * All endpoints start with /follows
 */

package com.connectsphere.follow.controller;

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

    /** POST /follows/{followerId}/follow/{followingId} — Follow a user */
    @PostMapping("/{followerId}/follow/{followingId}")
    public ResponseEntity<?> follow(@PathVariable Long followerId, @PathVariable Long followingId) {
        return ResponseEntity.ok(followService.follow(followerId, followingId));
    }

    /** DELETE /follows/{followerId}/unfollow/{followingId} — Unfollow a user */
    @DeleteMapping("/{followerId}/unfollow/{followingId}")
    public ResponseEntity<Void> unfollow(@PathVariable Long followerId, @PathVariable Long followingId) {
        followService.unfollow(followerId, followingId);
        return ResponseEntity.noContent().build();
    }

    /** GET /follows/{userId}/following — Get list of user IDs this user follows */
    @GetMapping("/{userId}/following")
    public ResponseEntity<List<Long>> following(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowing(userId));
    }

    /** GET /follows/{userId}/followers — Get list of user IDs that follow this user */
    @GetMapping("/{userId}/followers")
    public ResponseEntity<List<Long>> followers(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getFollowers(userId));
    }

    /** GET /follows/{userId}/counts — Get follower and following counts */
    @GetMapping("/{userId}/counts")
    public ResponseEntity<Map<String, Long>> counts(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getCounts(userId));
    }

    /**
     * GET /follows/{followerId}/is-following/{followingId} — Check follow status
     * Returns: {"following": true} or {"following": false}
     */
    @GetMapping("/{followerId}/is-following/{followingId}")
    public ResponseEntity<Map<String, Boolean>> isFollowing(@PathVariable Long followerId,
                                                             @PathVariable Long followingId) {
        return ResponseEntity.ok(Map.of("following",
                followService.isFollowing(followerId, followingId)));
    }

    /**
     * GET /follows/{userA}/mutual/{userB} — Check if two users follow each other
     * Returns: {"mutual": true} or {"mutual": false}
     */
    @GetMapping("/{userA}/mutual/{userB}")
    public ResponseEntity<Map<String, Boolean>> mutual(@PathVariable Long userA,
                                                        @PathVariable Long userB) {
        return ResponseEntity.ok(Map.of("mutual", followService.isMutual(userA, userB)));
    }

    /**
     * GET /follows/{userId}/mutual-list/{otherUserId} — Get mutual connections
     * Returns list of user IDs that both users follow
     */
    @GetMapping("/{userId}/mutual-list/{otherUserId}")
    public ResponseEntity<List<Long>> mutualList(@PathVariable Long userId,
                                                  @PathVariable Long otherUserId) {
        return ResponseEntity.ok(followService.getMutualFollowers(userId, otherUserId));
    }

    /**
     * GET /follows/{userId}/suggestions — Get friend suggestions
     * Returns list of user IDs suggested to follow (friends-of-friends)
     */
    @GetMapping("/{userId}/suggestions")
    public ResponseEntity<List<Long>> suggestions(@PathVariable Long userId) {
        return ResponseEntity.ok(followService.getSuggestedUsers(userId));
    }
}
