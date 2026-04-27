/**
 * Like.java — Like/Reaction Entity (Database Table Mapping)
 *
 * Represents the "likes" table in likedb database.
 * Handles reactions on BOTH posts and comments.
 *
 * Key design:
 *   - Unique constraint: one reaction per user per target
 *     (userId + targetId + targetType must be unique)
 *   - If user reacts again → update existing reaction type (don't create duplicate)
 *   - targetType distinguishes between post reactions and comment reactions
 *
 * @Data — Lombok annotation that generates:
 *   - getters and setters for all fields
 *   - toString(), equals(), hashCode() methods
 *   Saves writing boilerplate code
 *
 * @UniqueConstraint — database-level constraint
 *   Prevents duplicate reactions at the database level
 *   Even if application logic fails, DB won't allow duplicates
 */

package com.connectsphere.like.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "targetId", "targetType"}))
@Data
public class Like {

    /** likeId — Auto-generated primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long likeId;

    /** userId — Who reacted */
    private Long userId;

    /** targetId — ID of the post or comment being reacted to */
    private Long targetId;

    /**
     * targetType — Whether the reaction is on a POST or COMMENT
     * Stored as string in database ("POST" or "COMMENT")
     * Allows same table to handle reactions for both posts and comments
     */
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    /**
     * reactionType — Which emoji reaction was used
     * Stored as string in database ("LIKE", "LOVE", etc.)
     * Default is LIKE
     *
     * 6 reaction types matching Facebook-style reactions:
     *   LIKE  → 👍
     *   LOVE  → ❤️
     *   HAHA  → 😂
     *   WOW   → 😮
     *   SAD   → 😢
     *   ANGRY → 😡
     */
    @Enumerated(EnumType.STRING)
    private ReactionType reactionType = ReactionType.LIKE;

    /** createdAt — When the reaction was made */
    private LocalDateTime createdAt = LocalDateTime.now();

    /** TargetType enum — what the reaction is on */
    public enum TargetType { POST, COMMENT }

    /** ReactionType enum — which reaction was used */
    public enum ReactionType { LIKE, LOVE, HAHA, WOW, SAD, ANGRY }
}
