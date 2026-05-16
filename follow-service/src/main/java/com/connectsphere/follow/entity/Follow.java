/**
 * Follow.java — Follow Relationship Entity
 *
 * Represents the "follows" table in followdb database.
 * Each row = one follow relationship (user A follows user B)
 *
 * Unique constraint: (followerId, followingId)
 * Prevents a user from following the same person twice
 * Database enforces this even if application logic fails
 */

package com.connectsphere.follow.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "follows",
    uniqueConstraints = @UniqueConstraint(columnNames = {"followerId", "followingId"}))
@Data
public class Follow {

    /** followId — Auto-generated primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long followId;

    /** followerId — The user who is following (the follower) */
    private Long followerId;

    /** followingId — The user being followed */
    private Long followingId;

    /** createdAt — When the follow relationship was created */
    private LocalDateTime createdAt = LocalDateTime.now();
}
