package com.connectsphere.comment.service;

import com.connectsphere.comment.entity.Comment;
import com.connectsphere.comment.exception.BadRequestException;
import com.connectsphere.comment.exception.ResourceNotFoundException;
import com.connectsphere.comment.repository.CommentRepository;
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

/**
 * CommentService — Business logic for comments and replies.
 *
 * Handles:
 *   - Adding top-level comments and replies
 *   - Sending notifications via RabbitMQ
 *   - Incrementing post comment count via post-service
 *   - Editing and soft/hard deleting comments
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final RabbitTemplate rabbitTemplate;
    private final RestTemplate restTemplate;

    @Value("${post.service.url}")
    private String postServiceUrl;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    /**
     * addComment() — Adds a comment or reply to a post.
     *
     * Validates all required fields, saves the comment, increments
     * the post's comment count, and sends a notification to the
     * post owner (for comments) or parent comment author (for replies).
     *
     * @param postId          ID of the post being commented on
     * @param userId          ID of the commenter
     * @param username        Username of the commenter
     * @param content         Comment text
     * @param parentCommentId null for top-level comment, ID for reply
     */
    public Comment addComment(Long postId, Long userId, String username,
                               String content, Long parentCommentId) {
        /* Validate required fields */
        if (postId == null) throw new BadRequestException("postId is required.");
        if (userId == null) throw new BadRequestException("userId is required.");
        if (username == null || username.isBlank()) throw new BadRequestException("username is required.");
        if (content == null || content.isBlank()) throw new BadRequestException("Comment content cannot be empty.");

        log.info("User {} adding comment on post id={}", username, postId);

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setUsername(username);
        comment.setContent(content);
        comment.setParentCommentId(parentCommentId);
        Comment saved = commentRepository.save(comment);
        log.info("Comment saved with id={} on post id={}", saved.getCommentId(), postId);

        /* Increment comment count on the post */
        try {
            restTemplate.put(postServiceUrl + "/posts/" + postId + "/comments/increment", null);
        } catch (Exception e) {
            log.warn("Failed to increment comment count for post id={}: {}", postId, e.getMessage());
        }

        if (parentCommentId != null) {
            /* Reply — notify parent comment author */
            try {
                commentRepository.findById(parentCommentId).ifPresent(parent -> {
                    if (!parent.getUserId().equals(userId)) {
                        rabbitTemplate.convertAndSend(
                            "connectsphere.events", "reply.created",
                            Map.of(
                                "recipientId", parent.getUserId(),
                                "type", "REPLY",
                                "actorId", userId,
                                "message", username + " replied to your comment",
                                "targetId", postId,
                                "deepLink", frontendUrl + "/post/" + postId
                            ));
                        log.info("Reply notification sent to userId={}", parent.getUserId());
                    }
                });
            } catch (Exception e) {
                log.warn("Failed to send reply notification: {}", e.getMessage());
            }
        } else {
            /* Top-level comment — notify post owner */
            try {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> post = restTemplate.getForObject(
                    postServiceUrl + "/posts/" + postId, java.util.Map.class);

                if (post != null && post.get("userId") != null) {
                    Long postOwnerId = Long.parseLong(post.get("userId").toString());
                    if (!postOwnerId.equals(userId)) {
                        rabbitTemplate.convertAndSend(
                            "connectsphere.events", "comment.created",
                            Map.of(
                                "recipientId", postOwnerId,
                                "type", "COMMENT",
                                "actorId", userId,
                                "message", username + " commented on your post",
                                "targetId", postId,
                                "deepLink", frontendUrl + "/post/" + postId
                            ));
                        log.info("Comment notification sent to userId={}", postOwnerId);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to send comment notification for post id={}: {}", postId, e.getMessage());
            }
        }
        return saved;
    }

    /**
     * getByPost() — Returns top-level, non-deleted comments for a post, oldest first.
     *
     * @param postId ID of the post
     */
    public List<Comment> getByPost(Long postId) {
        log.debug("Fetching comments for post id={}", postId);
        return commentRepository
            .findByPostIdAndParentCommentIdIsNullAndDeletedFalseOrderByCreatedAtAsc(postId);
    }

    /**
     * getReplies() — Returns non-deleted replies to a comment, oldest first.
     *
     * @param parentCommentId ID of the parent comment
     */
    public List<Comment> getReplies(Long parentCommentId) {
        log.debug("Fetching replies for comment id={}", parentCommentId);
        return commentRepository
            .findByParentCommentIdAndDeletedFalseOrderByCreatedAtAsc(parentCommentId);
    }

    /**
     * editComment() — Updates comment content.
     * Throws ResourceNotFoundException if comment does not exist.
     *
     * @param commentId ID of the comment to edit
     * @param content   New content text
     */
    public Comment editComment(Long commentId, String content) {
        if (content == null || content.isBlank())
            throw new BadRequestException("Comment content cannot be empty.");

        log.info("Editing comment id={}", commentId);
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));
        comment.setContent(content);
        comment.setUpdatedAt(LocalDateTime.now());
        return commentRepository.save(comment);
    }

    /**
     * softDeleteComment() — Marks a comment as deleted without removing from DB.
     * Silently does nothing if the comment does not exist.
     *
     * @param commentId ID of the comment to soft-delete
     */
    public void softDeleteComment(Long commentId) {
        log.info("Soft-deleting comment id={}", commentId);
        commentRepository.findById(commentId).ifPresent(c -> {
            c.setDeleted(true);
            c.setUpdatedAt(LocalDateTime.now());
            commentRepository.save(c);
        });
    }

    /**
     * deleteComment() — Hard deletes a comment permanently (admin only).
     *
     * @param commentId ID of the comment to delete
     */
    public void deleteComment(Long commentId) {
        log.info("Hard-deleting comment id={}", commentId);
        commentRepository.deleteById(commentId);
    }

    /** getAllComments() — Returns all comments for admin management. */
    public List<Comment> getAllComments() {
        log.debug("Fetching all comments (admin)");
        return commentRepository.findAll();
    }

    public void reportComment(Long commentId, String reason) {
        if (reason == null || reason.isBlank())
            throw new BadRequestException("Report reason is required.");
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));
        comment.setReported(true);
        comment.setReportReason(reason);
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.save(comment);
    }

    public List<Comment> getReportedComments() {
        return commentRepository.findByReportedTrueOrderByCreatedAtDesc();
    }

    public void clearReport(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new ResourceNotFoundException("Comment not found with id: " + commentId));
        comment.setReported(false);
        comment.setReportReason(null);
        comment.setUpdatedAt(LocalDateTime.now());
        commentRepository.save(comment);
    }
}
