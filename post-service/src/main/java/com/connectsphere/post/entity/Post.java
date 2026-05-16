/**
 * Post.java — Post Entity (Database Table Mapping)
 *
 * Represents the "posts" table in postdb database.
 * Each row = one post created by a user.
 *
 * Key design decisions:
 *   - Soft delete: posts are never truly deleted, just marked deleted=true
 *   - Denormalized username: stored directly to avoid joining with auth-service
 *   - likesCount/commentsCount: cached counts updated by like/comment services
 *   - Visibility: controls who can see the post
 */

package com.connectsphere.post.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {

    /** postId — Auto-generated primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    /** userId — ID of the user who created this post (from auth-service) */
    private Long userId;

    /**
     * username — Username of the post creator
     * Stored here to avoid calling auth-service every time we display a post
     * This is called "denormalization" — trading storage for performance
     */
    private String username;

    /**
     * content — The text content of the post
     * columnDefinition = "TEXT" — allows longer text than VARCHAR(255)
     * Can contain #hashtags and @mentions
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** mediaUrl — URL of attached image or video (stored in media-service) */
    private String mediaUrl;

    /**
     * visibility — Who can see this post
     * PUBLIC    → everyone (including guests)
     * FOLLOWERS → only users who follow the post owner
     * PRIVATE   → only the post owner
     * Default is PUBLIC
     */
    @Enumerated(EnumType.STRING)
    private Visibility visibility = Visibility.PUBLIC;

    /**
     * likesCount — Cached count of total reactions on this post
     * Updated by like-service when someone reacts or unreacts
     * Cached here to avoid querying like-service for every post display
     */
    private int likesCount = 0;

    /**
     * commentsCount — Cached count of comments on this post
     * Updated by comment-service when someone adds a comment
     */
    private int commentsCount = 0;

    /**
     * reported — Whether this post has been reported by a user
     * false = not reported (default)
     * true  = reported, admin should review
     */
    private boolean reported = false;

    /** reportReason — The reason given when reporting this post */
    private String reportReason;

    /** boosted — Whether this post has been boosted (paid feature) */
    private boolean boosted = false;

    /** boostedAt — When the post was boosted */
    private LocalDateTime boostedAt;

    /**
     * deleted — Soft delete flag
     * false = post is active and visible (default)
     * true  = post is "deleted" but still in database
     *
     * Why soft delete?
     *   - Admin can review deleted posts
     *   - Data can be recovered if deleted by mistake
     *   - Audit trail is maintained
     * All queries filter with "AND deleted=false" to hide deleted posts
     */
    private boolean deleted = false;

    /**
     * mediaDeleted — Whether the attached media has been marked as deleted
     * Set to true when post is soft-deleted (media URL retained for audit)
     */
    private boolean mediaDeleted = false;

    /** mediaDeletedAt — When the media was marked as deleted */
    private LocalDateTime mediaDeletedAt;

    /** createdAt — When the post was created (set automatically) */
    private LocalDateTime createdAt = LocalDateTime.now();

    /** updatedAt — When the post was last modified */
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Visibility enum — the three visibility levels */
    public enum Visibility { PUBLIC, FOLLOWERS, PRIVATE }

    /* Standard getters and setters for all fields */
    public Long getPostId() { return postId; }
    public void setPostId(Long postId) { this.postId = postId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public Visibility getVisibility() { return visibility; }
    public void setVisibility(Visibility visibility) { this.visibility = visibility; }
    public int getLikesCount() { return likesCount; }
    public void setLikesCount(int likesCount) { this.likesCount = likesCount; }
    public int getCommentsCount() { return commentsCount; }
    public void setCommentsCount(int commentsCount) { this.commentsCount = commentsCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public boolean isReported() { return reported; }
    public void setReported(boolean reported) { this.reported = reported; }
    public String getReportReason() { return reportReason; }
    public void setReportReason(String reportReason) { this.reportReason = reportReason; }
    public boolean isBoosted() { return boosted; }
    public void setBoosted(boolean boosted) { this.boosted = boosted; }
    public LocalDateTime getBoostedAt() { return boostedAt; }
    public void setBoostedAt(LocalDateTime boostedAt) { this.boostedAt = boostedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public boolean isMediaDeleted() { return mediaDeleted; }
    public void setMediaDeleted(boolean mediaDeleted) { this.mediaDeleted = mediaDeleted; }
    public LocalDateTime getMediaDeletedAt() { return mediaDeletedAt; }
    public void setMediaDeletedAt(LocalDateTime mediaDeletedAt) { this.mediaDeletedAt = mediaDeletedAt; }
}
