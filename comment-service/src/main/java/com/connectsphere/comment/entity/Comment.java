/**
 * Comment.java — Comment Entity (Database Table Mapping)
 *
 * Represents the "comments" table in commentdb database.
 * Supports TWO levels of threading:
 *   Level 1: Top-level comment (parentCommentId = null)
 *   Level 2: Reply to a comment (parentCommentId = ID of parent comment)
 *
 * Threading design:
 *   Post
 *   └── Comment A (parentCommentId = null)
 *       └── Reply 1 (parentCommentId = Comment A's ID)
 *       └── Reply 2 (parentCommentId = Comment A's ID)
 *   └── Comment B (parentCommentId = null)
 */

package com.connectsphere.comment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
public class Comment {

    /** commentId — Auto-generated primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long commentId;

    /** postId — Which post this comment belongs to */
    private Long postId;

    /** userId — Who wrote this comment */
    private Long userId;

    /**
     * username — Username of the commenter
     * Stored here to avoid calling auth-service for every comment display
     */
    private String username;

    /** content — The actual comment text (TEXT type for longer comments) */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * parentCommentId — For threading (replies)
     * null = this is a top-level comment on a post
     * non-null = this is a reply to another comment
     */
    private Long parentCommentId;

    /**
     * deleted — Soft delete flag
     * true = comment is hidden but still in database
     * false = comment is visible (default)
     */
    private boolean deleted = false;

    /** createdAt — When the comment was created */
    private LocalDateTime createdAt = LocalDateTime.now();

    /** updatedAt — When the comment was last edited */
    private LocalDateTime updatedAt = LocalDateTime.now();

    /* Getters and Setters */
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Long getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(Long parentCommentId) { this.parentCommentId = parentCommentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
