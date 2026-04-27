/**
 * FollowService.java — Follow Business Logic
 *
 * Handles all follow/unfollow operations:
 *   - Follow a user (with notification)
 *   - Unfollow a user
 *   - Get following/followers lists
 *   - Check follow status
 *   - Find mutual connections
 *   - Suggest users to follow (friends-of-friends algorithm)
 */

package com.connectsphere.follow.service;

import com.connectsphere.follow.entity.Follow;
import com.connectsphere.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    private final FollowRepository followRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    /** URL of auth-service to get follower's username for notification */
    @Value("${auth.service.url}")
    private String authServiceUrl;

    /**
     * follow() — Creates a follow relationship
     *
     * Steps:
     *   1. Check if already following → throw error if yes
     *   2. Save follow relationship to database
     *   3. Get follower's username from auth-service
     *   4. Publish follow notification to RabbitMQ
     *
     * Why get username from auth-service?
     *   follow-service only stores IDs, not usernames.
     *   We need the username for the notification message.
     *   Falls back to userId string if auth-service call fails.
     */
    public Follow follow(Long followerId, Long followingId) {
        /* Prevent duplicate follows */
        if (followerId == null || followingId == null)
            throw new RuntimeException("followerId and followingId are required.");
        if (followerId.equals(followingId))
            throw new RuntimeException("You cannot follow yourself.");
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId))
            throw new RuntimeException("Already following this user.");

        log.info("User {} following user {}", followerId, followingId);

        /* Save the follow relationship */
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        Follow saved = followRepository.save(follow);
        log.info("Follow saved: follower={} following={}", followerId, followingId);

        /* Send follow notification */
        try {
            /* Try to get follower's username for a friendly notification message */
            String followerUsername = followerId.toString(); /* Default to ID if fetch fails */
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> u = restTemplate.getForObject(
                    authServiceUrl + "/auth/user/" + followerId, Map.class);
                if (u != null && u.get("username") != null) {
                    followerUsername = (String) u.get("username");
                }
            } catch (Exception ignored) {}

            /* Publish follow notification to RabbitMQ */
            rabbitTemplate.convertAndSend(
                "connectsphere.events", "follow.created",
                Map.of(
                    "recipientId", followingId,  /* Person being followed gets notified */
                    "type", "FOLLOW",
                    "message", followerUsername + " started following you",
                    "actorId", followerId
                ));
        } catch (Exception e) {
            log.warn("Failed to send follow notification: {}", e.getMessage());
        }

        return saved;
    }

    /**
     * unfollow() — Removes a follow relationship
     *
     * .ifPresent() — only deletes if the follow relationship exists
     * No error if trying to unfollow someone you don't follow
     * No notification sent for unfollowing
     */
    public void unfollow(Long followerId, Long followingId) {
        log.info("User {} unfollowing user {}", followerId, followingId);
        followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .ifPresent(followRepository::delete);
    }

    /**
     * getFollowing() — Gets list of user IDs that a user follows
     *
     * .stream() — converts list to stream
     * .map(Follow::getFollowingId) — extracts just the followingId from each Follow object
     * .collect(Collectors.toList()) — converts stream back to list
     *
     * Returns: [userId1, userId2, userId3, ...]
     */
    public List<Long> getFollowing(Long userId) {
        return followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFollowingId)
                .collect(Collectors.toList());
    }

    /**
     * getFollowers() — Gets list of user IDs that follow a user
     * Returns: [userId1, userId2, ...] — IDs of people who follow this user
     */
    public List<Long> getFollowers(Long userId) {
        return followRepository.findByFollowingId(userId).stream()
                .map(Follow::getFollowerId)
                .collect(Collectors.toList());
    }

    /**
     * isFollowing() — Checks if one user follows another
     * Returns true/false
     * Used by Profile page to show Follow/Following button state
     */
    public boolean isFollowing(Long followerId, Long followingId) {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    /**
     * isMutual() — Checks if two users follow each other
     * A follows B AND B follows A = mutual
     * Used to show "Mutual" badge in followers/following lists
     */
    public boolean isMutual(Long userA, Long userB) {
        return followRepository.existsByFollowerIdAndFollowingId(userA, userB)
                && followRepository.existsByFollowerIdAndFollowingId(userB, userA);
    }

    /**
     * getMutualFollowers() — Gets list of users both people follow
     *
     * Algorithm:
     *   1. Get list of users that userId follows (myFollowing)
     *   2. Get list of users that otherUserId follows (theirFollowing)
     *   3. Return the intersection (users in BOTH lists)
     *
     * .filter(theirFollowing::contains) — keeps only IDs that appear in both lists
     * Used to show "Mutual" badges in followers/following modal
     */
    public List<Long> getMutualFollowers(Long userId, Long otherUserId) {
        List<Long> myFollowing = getFollowing(userId);
        List<Long> theirFollowing = getFollowing(otherUserId);
        return myFollowing.stream()
            .filter(theirFollowing::contains)
            .collect(Collectors.toList());
    }

    /**
     * getSuggestedUsers() — Friend suggestion algorithm (friends-of-friends)
     *
     * Algorithm:
     *   1. Get list of users I already follow (alreadyFollowing)
     *   2. For each person I follow → get THEIR following list
     *   3. Combine all those users into one big list
     *   4. Filter out: myself + people I already follow
     *   5. Remove duplicates (.distinct())
     *   6. Return up to 10 suggestions (.limit(10))
     *
     * Example:
     *   I follow: [Alice, Bob]
     *   Alice follows: [Charlie, Dave]
     *   Bob follows: [Dave, Eve]
     *   Suggestions: [Charlie, Dave, Eve] (Dave appears once due to .distinct())
     *
     * .flatMap() — flattens nested lists into one stream
     *   [Alice's following, Bob's following] → [Charlie, Dave, Dave, Eve]
     */
    public List<Long> getSuggestedUsers(Long userId) {
        List<Long> alreadyFollowing = getFollowing(userId);
        return alreadyFollowing.stream()
            .flatMap(followingId -> getFollowing(followingId).stream())
            .filter(id -> !id.equals(userId) && !alreadyFollowing.contains(id))
            .distinct()
            .limit(10)
            .collect(Collectors.toList());
    }

    /**
     * getCounts() — Gets follower and following counts for a user
     * Returns: {"following": 25, "followers": 100}
     * Used by Profile page to show stats
     */
    public Map<String, Long> getCounts(Long userId) {
        return Map.of(
                "following", followRepository.countByFollowerId(userId),
                "followers", followRepository.countByFollowingId(userId));
    }
}
