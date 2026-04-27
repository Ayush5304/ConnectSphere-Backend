package com.connectsphere.like.service;

import com.connectsphere.like.entity.Like;
import com.connectsphere.like.exception.BadRequestException;
import com.connectsphere.like.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LikeService — Business logic for reactions/likes.
 *
 * Handles:
 *   - Adding or changing reactions (react)
 *   - Removing reactions (unreact)
 *   - Fetching reaction summaries
 *   - Publishing like notifications via RabbitMQ
 */
@Service
@RequiredArgsConstructor
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeRepository likeRepository;
    private final RestTemplate restTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Value("${post.service.url}")
    private String postServiceUrl;

    @Value("${auth.service.url:http://localhost:8081}")
    private String authServiceUrl;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * react() — Adds a new reaction or updates an existing one.
     *
     * If user already reacted → update reaction type only (no duplicate, no new notification).
     * If new reaction → save, increment post likes count, send notification.
     *
     * @param userId       ID of the user reacting
     * @param targetId     ID of the post or comment
     * @param targetType   POST or COMMENT
     * @param reactionType LIKE / LOVE / HAHA / WOW / SAD / ANGRY
     * @throws BadRequestException if any required parameter is null
     */
    public Like react(Long userId, Long targetId, Like.TargetType targetType,
                      Like.ReactionType reactionType) {

        /* Validate required fields */
        if (userId == null || targetId == null || targetType == null || reactionType == null) {
            throw new BadRequestException("userId, targetId, targetType, and reactionType are required.");
        }

        log.info("User {} reacting with {} on {} id={}", userId, reactionType, targetType, targetId);

        Optional<Like> existing = likeRepository
            .findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType);

        if (existing.isPresent()) {
            /* User already reacted — update reaction type only */
            Like like = existing.get();
            like.setReactionType(reactionType);
            Like updated = likeRepository.save(like);
            log.info("Updated reaction for user {} on {} id={} to {}", userId, targetType, targetId, reactionType);
            return updated;
        }

        /* New reaction — create record */
        Like like = new Like();
        like.setUserId(userId);
        like.setTargetId(targetId);
        like.setTargetType(targetType);
        like.setReactionType(reactionType);
        Like saved = likeRepository.save(like);
        log.info("New reaction saved: user={} type={} target={} id={}", userId, reactionType, targetType, targetId);

        if (targetType == Like.TargetType.POST) {
            incrementPostLikes(targetId);
            sendLikeNotification(userId, targetId, reactionType);
        }

        return saved;
    }

    /**
     * unreact() — Removes a user's reaction from a target.
     * Silently does nothing if the reaction does not exist.
     *
     * @param userId     ID of the user removing the reaction
     * @param targetId   ID of the post or comment
     * @param targetType POST or COMMENT
     * @throws BadRequestException if any required parameter is null
     */
    public void unreact(Long userId, Long targetId, Like.TargetType targetType) {
        if (userId == null || targetId == null || targetType == null) {
            throw new BadRequestException("userId, targetId, and targetType are required.");
        }

        log.info("User {} removing reaction from {} id={}", userId, targetType, targetId);

        likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType)
            .ifPresent(like -> {
                likeRepository.delete(like);
                log.info("Reaction removed for user={} on {} id={}", userId, targetType, targetId);
                if (targetType == Like.TargetType.POST) {
                    decrementPostLikes(targetId);
                }
            });
    }

    /**
     * getReactions() — Returns all reactions on a given target.
     *
     * @param targetId   ID of the post or comment
     * @param targetType POST or COMMENT
     */
    public List<Like> getReactions(Long targetId, Like.TargetType targetType) {
        log.debug("Fetching reactions for {} id={}", targetType, targetId);
        return likeRepository.findByTargetIdAndTargetType(targetId, targetType);
    }

    /**
     * getReactionSummary() — Returns reaction counts grouped by type.
     * Example: {"LIKE": 5, "LOVE": 3}. Only includes types with count > 0.
     *
     * @param targetId   ID of the post or comment
     * @param targetType POST or COMMENT
     */
    public Map<String, Long> getReactionSummary(Long targetId, Like.TargetType targetType) {
        log.debug("Fetching reaction summary for {} id={}", targetType, targetId);
        List<Like> reactions = likeRepository.findByTargetIdAndTargetType(targetId, targetType);
        Map<String, Long> summary = new java.util.LinkedHashMap<>();
        for (Like.ReactionType type : Like.ReactionType.values()) {
            long count = reactions.stream()
                .filter(r -> r.getReactionType() == type)
                .count();
            if (count > 0) summary.put(type.name(), count);
        }
        return summary;
    }

    /**
     * getUserReaction() — Returns the current user's reaction on a target, if any.
     *
     * @param userId     ID of the user
     * @param targetId   ID of the post or comment
     * @param targetType POST or COMMENT
     */
    public Optional<Like> getUserReaction(Long userId, Long targetId, Like.TargetType targetType) {
        log.debug("Fetching reaction for user={} on {} id={}", userId, targetType, targetId);
        return likeRepository.findByUserIdAndTargetIdAndTargetType(userId, targetId, targetType);
    }

    /* ── Private helpers ─────────────────────────────────────────────── */

    /** Calls post-service to increment the likes count on a post. */
    private void incrementPostLikes(Long postId) {
        try {
            restTemplate.put(postServiceUrl + "/posts/" + postId + "/likes/increment", null);
            log.debug("Incremented likes count for post id={}", postId);
        } catch (Exception e) {
            log.warn("Failed to increment likes count for post id={}: {}", postId, e.getMessage());
        }
    }

    /** Calls post-service to decrement the likes count on a post. */
    private void decrementPostLikes(Long postId) {
        try {
            restTemplate.put(postServiceUrl + "/posts/" + postId + "/likes/decrement", null);
            log.debug("Decremented likes count for post id={}", postId);
        } catch (Exception e) {
            log.warn("Failed to decrement likes count for post id={}: {}", postId, e.getMessage());
        }
    }

    /**
     * Fetches the post owner and publishes a like notification to RabbitMQ.
     * Does not notify if the reactor is the post owner.
     */
    private void sendLikeNotification(Long userId, Long targetId, Like.ReactionType reactionType) {
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> post = restTemplate.getForObject(
                postServiceUrl + "/posts/" + targetId, java.util.Map.class);

            if (post != null && post.get("userId") != null) {
                Long postOwnerId = Long.parseLong(post.get("userId").toString());
                if (!postOwnerId.equals(userId)) {
                    /* Fetch actor username for a friendly notification message */
                    String actorUsername = String.valueOf(userId);
                    try {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> actor = restTemplate.getForObject(
                            authServiceUrl + "/auth/user/" + userId, java.util.Map.class);
                        if (actor != null && actor.get("username") != null) {
                            actorUsername = actor.get("username").toString();
                        }
                    } catch (Exception ignored) {}

                    String emoji = switch (reactionType) {
                        case LIKE  -> "👍";
                        case LOVE  -> "❤️";
                        case HAHA  -> "😂";
                        case WOW   -> "😮";
                        case SAD   -> "😢";
                        case ANGRY -> "😡";
                    };

                    rabbitTemplate.convertAndSend(
                        "connectsphere.events", "like.created",
                        Map.of(
                            "recipientId", postOwnerId,
                            "type", "LIKE",
                            "actorId", userId,
                            "message", actorUsername + " reacted " + emoji + " to your post",
                            "targetId", targetId,
                            "deepLink", frontendUrl + "/post/" + targetId
                        ));
                    log.info("Like notification sent to user={} for post id={}", postOwnerId, targetId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send like notification for post id={}: {}", targetId, e.getMessage());
        }
    }
}
