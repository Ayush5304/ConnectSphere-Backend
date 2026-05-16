/**
 * Story.java — Story Entity
 *
 * Represents the "stories" table in mediadb database.
 * Stories are temporary media posts that expire after 24 hours.
 *
 * Key features:
 *   - Auto-set createdAt and expiresAt using @PrePersist
 *   - viewCount tracks how many unique users viewed the story
 *   - Expired stories are automatically deleted by scheduled job
 */

package com.connectsphere.media.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "stories")
@Data
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long storyId;

    /** userId — Who posted this story */
    private Long userId;

    /** username — Story poster's username (stored to avoid auth-service calls) */
    private String username;

    /** mediaUrl — URL of the story image or video */
    private String mediaUrl;

    /** mediaType — MIME type of the media (image/jpeg, video/mp4 etc.) */
    private String mediaType;

    /** viewCount — Number of unique users who viewed this story */
    private int viewCount = 0;

    /** expired - Whether this story has been marked as expired */
    private boolean expired = false;

    /** reported - Moderation flag set when a viewer reports the story */
    private boolean reported = false;

    /** reportReason - Latest reason provided by a viewer or admin */
    private String reportReason;

/** createdAt — When the story was posted (set automatically) */
    private LocalDateTime createdAt;

    /**
     * expiresAt — When the story expires (24 hours after creation)
     * Set automatically in @PrePersist
     * Scheduled job checks this to delete expired stories
     */
    private LocalDateTime expiresAt;

    /**
     * @PrePersist — Runs automatically BEFORE the entity is saved to database
     * Sets createdAt to current time
     * Sets expiresAt to 24 hours from now
     * This ensures every story automatically gets the correct timestamps
     */
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        expiresAt = LocalDateTime.now().plusHours(24);
    }
}
