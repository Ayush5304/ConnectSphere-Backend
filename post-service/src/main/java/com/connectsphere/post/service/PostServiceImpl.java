/**
 * PostServiceImpl.java — Post Service Implementation
 *
 * Contains all business logic for post operations.
 * Implements the PostService interface.
 *
 * Key integrations:
 *   - search-service: indexes hashtags when post is created/edited
 *   - auth-service: resolves @mention usernames to user IDs
 *   - RabbitMQ: publishes mention notification events
 *
 * @Service — Spring manages this as a singleton bean
 */

package com.connectsphere.post.service;

import com.connectsphere.post.entity.Post;
import com.connectsphere.post.exception.BadRequestException;
import com.connectsphere.post.exception.ResourceNotFoundException;
import com.connectsphere.post.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PostServiceImpl implements PostService {

    private static final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);

    /** postRepository — database operations for posts table */
    private final PostRepository postRepository;

    /**
     * rabbitTemplate — sends messages to RabbitMQ
     * Used to publish mention notification events
     */
    private final RabbitTemplate rabbitTemplate;

    /**
     * restTemplate — makes HTTP calls to other services
     * Used to call search-service and auth-service
     */
    private final RestTemplate restTemplate;

    /** URL of search-service for hashtag indexing */
    @Value("${search.service.url}")
    private String searchServiceUrl;

    /** URL of auth-service for resolving @mention usernames */
    @Value("${auth.service.url}")
    private String authServiceUrl;

    /**
     * MENTION_PATTERN — Regex pattern to find @mentions in post content
     * @(\\w+) matches @ followed by one or more word characters (letters, digits, underscore)
     * e.g. "Hello @Ayush how are you" → finds "Ayush"
     * Pattern.compile() compiles the regex once for reuse (better performance)
     */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");

    public PostServiceImpl(PostRepository postRepository, RabbitTemplate rabbitTemplate, RestTemplate restTemplate) {
        this.postRepository = postRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * createPost() — Creates a new post and triggers side effects
     *
     * Steps:
     *   1. Create and save the post to database
     *   2. Index hashtags in search-service (for trending and search)
     *   3. Find @mentions in content → resolve to user IDs → send notifications
     *
     * Why try-catch around side effects?
     *   If hashtag indexing or mention notification fails,
     *   the post should still be created successfully.
     *   Side effects are non-critical.
     */
    @Override
    public Post createPost(Long userId, String username, String content, String mediaUrl, Post.Visibility visibility) {
        /* Validate required fields */
        if (userId == null) throw new BadRequestException("userId is required.");
        if (username == null || username.isBlank()) throw new BadRequestException("username is required.");
        boolean hasContent = content != null && !content.isBlank();
        boolean hasMedia = mediaUrl != null && !mediaUrl.isBlank();
        if (!hasContent && !hasMedia) {
            throw new BadRequestException("Post must contain text or media.");
        }

        log.info("Creating post for user={} username={}", userId, username);

        /* Create and save the post */
        Post post = new Post();
        post.setUserId(userId);
        post.setUsername(username);
        post.setContent(content != null ? content : "");
        post.setMediaUrl(mediaUrl);
        post.setVisibility(visibility != null ? visibility : Post.Visibility.PUBLIC);
        Post saved = postRepository.save(post);
        log.info("Post created with id={} by user={}", saved.getPostId(), userId);

        /*
         * Index hashtags in search-service
         * Sends POST request to search-service with post content and postId
         * search-service extracts #tags and stores them with postCount
         * Wrapped in try-catch: if search-service is down, post still saves
         */
        try {
            restTemplate.postForObject(
                searchServiceUrl + "/hashtags/index",
                Map.of("content", content != null ? content : "", "postId", saved.getPostId()),
                Void.class);
        } catch (Exception e) {
            log.warn("Failed to index hashtags for post id={}: {}", saved.getPostId(), e.getMessage());
        }

        /*
         * Process @mentions and send notifications
         *
         * Steps for each @mention found:
         *   1. Extract the mentioned username (e.g. "khushi" from "@khushi")
         *   2. Call auth-service to search for that username
         *   3. Find exact match (case-insensitive)
         *   4. If found and it's not the post author → publish RabbitMQ event
         *
         * matcher.find() — finds next @mention in the content
         * matcher.group(1) — gets the captured group (username without @)
         */
        if (content != null) {
            Matcher matcher = MENTION_PATTERN.matcher(content);
            while (matcher.find()) {
                String mentionedUsername = matcher.group(1);
                try {
                    /* Call auth-service to find user by username */
                    @SuppressWarnings("unchecked")
                    java.util.List<java.util.Map<String, Object>> users = restTemplate.exchange(
                        authServiceUrl + "/auth/search?query=" + mentionedUsername,
                        org.springframework.http.HttpMethod.GET, null,
                        new org.springframework.core.ParameterizedTypeReference<
                            java.util.List<java.util.Map<String, Object>>>() {}).getBody();

                    if (users != null) {
                        users.stream()
                            /* Find exact username match (search returns partial matches too) */
                            .filter(u -> mentionedUsername.equalsIgnoreCase((String) u.get("username")))
                            .findFirst()
                            .ifPresent(u -> {
                                Long recipientId = Long.parseLong(u.get("userId").toString());
                                /* Don't notify yourself if you mention yourself */
                                if (!recipientId.equals(userId)) {
                                    /*
                                     * Publish mention notification to RabbitMQ
                                     * notification-service will pick this up and create a notification
                                     * "connectsphere.events" = exchange name
                                     * "mention.created" = routing key
                                     */
                                    rabbitTemplate.convertAndSend(
                                        "connectsphere.events", "mention.created",
                                        Map.of(
                                            "recipientId", recipientId,
                                            "type", "MENTION",
                                            "message", username + " mentioned you in a post",
                                            "targetId", saved.getPostId()
                                        ));
                                }
                            });
                    }
                } catch (Exception e) {
                    log.warn("Failed to send mention notification: {}", e.getMessage());
                }
            }
        }
        return saved;
    }

    /**
     * editPost() — Updates post content, media, or visibility
     *
     * Only updates fields that are NOT null (partial update).
     * Re-indexes hashtags after edit (content may have changed).
     * Updates the updatedAt timestamp.
     */
    @Override
    public Post editPost(Long postId, String content, String mediaUrl, Post.Visibility visibility) {
        log.info("Editing post id={}", postId);
        Post post = getById(postId);
        if (content != null) post.setContent(content);
        if (mediaUrl != null) post.setMediaUrl(mediaUrl);
        if (visibility != null) post.setVisibility(visibility);
        post.setUpdatedAt(LocalDateTime.now());
        Post updated = postRepository.save(post);

        /* Re-index hashtags since content may have changed */
        try {
            restTemplate.postForObject(
                searchServiceUrl + "/hashtags/index",
                Map.of("content", post.getContent() != null ? post.getContent() : "", "postId", postId),
                Void.class);
        } catch (Exception ignored) {}

        return updated;
    }

    /**
     * softDeletePost() — Marks post as deleted without removing from database
     *
     * Sets deleted=true so all queries will filter it out.
     * Also marks media as deleted (for audit purposes).
     * The actual media file in media-service is NOT deleted here.
     *
     * Why keep the data?
     *   - Admin can review deleted posts
     *   - Data recovery is possible
     *   - Audit trail maintained
     */
    /** softDeletePost() — Marks post as deleted. Evicts from Redis cache. */
    @Override
    @CacheEvict(value = {"publicFeed", "postById"}, allEntries = true)
    public void softDeletePost(Long postId) {
        log.info("Soft-deleting post id={}", postId);
        Post post = getById(postId);
        post.setDeleted(true);
        post.setUpdatedAt(LocalDateTime.now());
        /* Mark media as deleted if post had media */
        if (post.getMediaUrl() != null && !post.getMediaUrl().isBlank()) {
            post.setMediaDeleted(true);
            post.setMediaDeletedAt(LocalDateTime.now());
        }
        postRepository.save(post);
    }

    /**
     * getById() — Finds a post by ID
     * .orElseThrow() — throws exception if not found (returns 500 error)
     */
    /** getById() — Finds a post by ID. Cached in Redis for 10 minutes. */
    @Override
    @Cacheable(value = "postById", key = "#postId")
    public Post getById(Long postId) {
        log.debug("Fetching post id={}", postId);
        return postRepository.findById(postId)
            .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));
    }

    /**
     * getByUser() — Gets all non-deleted posts by a user, newest first
     * Used by Profile page to show user's posts
     */
    @Override
    public List<Post> getByUser(Long userId) {
        return postRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId);
    }

    /** getPublicFeed() — Gets all PUBLIC non-deleted posts, newest first. Cached in Redis for 5 minutes. */
    @Override
    //@Cacheable(value = "publicFeed")
    public List<Post> getPublicFeed() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * getFeedForUsers() — Gets posts from a list of users, newest first
     * Used for personalized feed: [followingId1, followingId2, ..., ownId]
     * findByUserIdIn — SQL: WHERE user_id IN (1, 2, 3, ...)
     */
    @Override
    public List<Post> getFeedForUsers(List<Long> userIds) {
        return postRepository.findByUserIdInAndDeletedFalseOrderByCreatedAtDesc(userIds);
    }

    /**
     * search() — Searches post content for a keyword
     * Only searches PUBLIC non-deleted posts
     * ContainingIgnoreCase — SQL: WHERE content LIKE '%keyword%' (case-insensitive)
     */
    @Override
    public List<Post> search(String keyword) {
        return postRepository.findByContentContainingIgnoreCaseAndVisibilityAndDeletedFalse(
            keyword, Post.Visibility.PUBLIC);
    }

    /** updateVisibility() — Changes who can see a post */
    @Override
    public Post updateVisibility(Long postId, Post.Visibility visibility) {
        Post post = getById(postId);
        post.setVisibility(visibility);
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    /**
     * deletePost() — Hard deletes a post permanently
     * Data cannot be recovered after this
     * Used by admin for permanent removal
     */
    @Override
    public void deletePost(Long postId) {
        log.info("Hard-deleting post id={}", postId);
        postRepository.deleteById(postId);
    }

    /**
     * incrementLikes() — Increases likesCount by 1
     *
     * @Transactional — ensures the read-modify-write is atomic
     * Without @Transactional, two simultaneous likes could both read
     * the same count and both write count+1 instead of count+2
     * (race condition / lost update problem)
     *
     * Called by like-service when someone reacts to a post
     */
    @Override
    @Transactional
    public void incrementLikes(Long postId) {
        log.debug("Incrementing likes for post id={}", postId);
        Post post = getById(postId);
        post.setLikesCount(post.getLikesCount() + 1);
        postRepository.save(post);
    }

    /**
     * decrementLikes() — Decreases likesCount by 1
     * Math.max(0, ...) — prevents count from going below 0
     * Called by like-service when someone removes their reaction
     */
    @Override
    @Transactional
    public void decrementLikes(Long postId) {
        log.debug("Decrementing likes for post id={}", postId);
        Post post = getById(postId);
        post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
        postRepository.save(post);
    }

    /**
     * incrementComments() — Increases commentsCount by 1
     * Called by comment-service when someone adds a comment
     */
    @Override
    @Transactional
    public void incrementComments(Long postId) {
        log.debug("Incrementing comments for post id={}", postId);
        Post post = getById(postId);
        post.setCommentsCount(post.getCommentsCount() + 1);
        postRepository.save(post);
    }

    /** countByUser() — Returns total number of posts by a user */
    @Override
    public long countByUser(Long userId) {
        return postRepository.countByUserId(userId);
    }

    /**
     * getAllPosts() — Returns ALL posts including deleted (for admin)
     * No deleted=false filter — admin sees everything
     */
    @Override
    public List<Post> getAllPosts() {
        return postRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * reportPost() — Marks a post as reported
     * Sets reported=true and saves the reason
     * Admin can see reported posts in dashboard
     */
    @Override
    public void reportPost(Long postId, String reason) {
        Post post = getById(postId);
        post.setReported(true);
        post.setReportReason(reason);
        postRepository.save(post);
    }

    /** getReportedPosts() — Returns all reported non-deleted posts for admin review */
    @Override
    public List<Post> getReportedPosts() {
        return postRepository.findByReportedTrueOrderByCreatedAtDesc();
    }

    /**
     * clearReport() — Removes the report flag from a post
     * Admin reviewed the post and decided it doesn't violate rules
     */
    @Override
    public void clearReport(Long postId) {
        Post post = getById(postId);
        post.setReported(false);
        post.setReportReason(null);
        postRepository.save(post);
    }

    /**
     * getAnalytics() — Returns post statistics for admin dashboard
     * countByDeletedFalse() — total active posts
     * countByVisibilityAndDeletedFalse() — public posts count
     * countByReportedTrueAndDeletedFalse() — reported posts count
     */
    @Override
    public Map<String, Object> getAnalytics() {
        long totalPosts = postRepository.countByDeletedFalse();
        long publicPosts = postRepository.countByVisibilityAndDeletedFalse(Post.Visibility.PUBLIC);
        long reportedPosts = postRepository.countByReportedTrueAndDeletedFalse();
        return Map.of("totalPosts", totalPosts, "publicPosts", publicPosts, "reportedPosts", reportedPosts);
    }

    /** boostPost() — Marks a post as boosted (called by payment-service after BOOST_POST payment) */
    @Override
    public void boostPost(Long postId) {
        log.info("Boosting post id={}", postId);
        Post post = getById(postId);
        post.setBoosted(true);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
    }
}
