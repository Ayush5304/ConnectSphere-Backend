/**
 * LikeResource.java — Like/Reaction REST Controller
 *
 * Handles all HTTP requests for reactions.
 * All endpoints start with /likes
 */

package com.connectsphere.like.controller;

import com.connectsphere.like.entity.Like;
import com.connectsphere.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
public class LikeResource {

    private final LikeService likeService;

    /**
     * POST /likes — Add or change a reaction
     *
     * Receives: {userId, targetId, targetType, reactionType}
     * body.getOrDefault("reactionType", "LIKE") — defaults to LIKE if not provided
     * Like.TargetType.valueOf() — converts "POST" string to enum TargetType.POST
     * Like.ReactionType.valueOf() — converts "LOVE" string to enum ReactionType.LOVE
     */
    @PostMapping
    public ResponseEntity<Like> react(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(likeService.react(
                Long.parseLong(body.get("userId")),
                Long.parseLong(body.get("targetId")),
                Like.TargetType.valueOf(body.get("targetType")),
                Like.ReactionType.valueOf(body.getOrDefault("reactionType", "LIKE"))));
    }

    /**
     * DELETE /likes?userId=1&targetId=5&targetType=POST — Remove a reaction
     *
     * Uses @RequestParam (URL query parameters) instead of path variables
     * because we need multiple parameters to identify the reaction
     */
    @DeleteMapping
    public ResponseEntity<Void> unreact(@RequestParam Long userId,
                                         @RequestParam Long targetId,
                                         @RequestParam String targetType) {
        likeService.unreact(userId, targetId, Like.TargetType.valueOf(targetType));
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /likes?targetId=5&targetType=POST — Get all reactions on a target
     * Returns list of all Like objects for the target
     */
    @GetMapping
    public ResponseEntity<List<Like>> getReactions(@RequestParam Long targetId,
                                                    @RequestParam String targetType) {
        return ResponseEntity.ok(likeService.getReactions(targetId, Like.TargetType.valueOf(targetType)));
    }

    /**
     * GET /likes/summary?targetId=5&targetType=POST — Get reaction counts by type
     * Returns: {"LIKE": 5, "LOVE": 3} — only types with count > 0
     * Used by PostCard to show reaction emojis and counts
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getReactionSummary(@RequestParam Long targetId,
                                                                  @RequestParam String targetType) {
        return ResponseEntity.ok(likeService.getReactionSummary(
            targetId, Like.TargetType.valueOf(targetType)));
    }

    /**
     * GET /likes/user?userId=1&targetId=5&targetType=POST — Get user's reaction
     *
     * Returns the user's current reaction on a target.
     * Used to show which reaction button is active (highlighted).
     *
     * .map(ResponseEntity::ok) — if reaction found, return 200 with Like object
     * .orElse(ResponseEntity.noContent().build()) — if not found, return 204 No Content
     */
    @GetMapping("/user")
    public ResponseEntity<Like> getUserReaction(@RequestParam Long userId,
                                                 @RequestParam Long targetId,
                                                 @RequestParam String targetType) {
        return likeService.getUserReaction(userId, targetId, Like.TargetType.valueOf(targetType))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }
}
