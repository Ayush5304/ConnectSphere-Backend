/**
 * Notification.java — Notification Entity
 *
 * Represents the "notifications" table in notificationdb.
 * Each row = one notification for a user.
 *
 * Notifications are created by:
 *   - RabbitMQ consumer (handleEvent) — for like, comment, reply, follow, mention
 *   - Admin global broadcast — for GLOBAL type notifications
 *   - Direct API call — for system notifications
 *
 * Notification types:
 *   LIKE    → someone reacted to your post
 *   COMMENT → someone commented on your post
 *   REPLY   → someone replied to your comment
 *   FOLLOW  → someone started following you
 *   MENTION → someone mentioned you in a post
 *   GLOBAL  → admin broadcast to all users
 *   SYSTEM  → system-generated notification
 */

package com.connectsphere.notification.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
public class Notification {

    /** notificationId — Auto-generated primary key */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long notificationId;

    /** recipientId — Who receives this notification */
    private Long recipientId;

    /** actorId — Who triggered this notification (who liked/commented/followed) */
    private Long actorId;

    /** targetId — ID of the related post or comment */
    private Long targetId;

    /** type — Category of notification (LIKE/COMMENT/REPLY/FOLLOW/MENTION/GLOBAL) */
    private String type;

    /** message — Human-readable notification text e.g. "khushi liked your post" */
    private String message;

    /**
     * deepLink — URL to navigate to when notification is clicked
     * e.g. "http://localhost:3000/post/5" for a like notification
     * e.g. "http://localhost:3000/profile/3" for a follow notification
     */
    private String deepLink;

    /**
     * isRead — Whether the user has seen this notification
     * false = unread (shown with blue dot, counted in badge)
     * true  = read (no badge, normal appearance)
     */
    private boolean isRead = false;

    /** createdAt — When the notification was created */
    private LocalDateTime createdAt = LocalDateTime.now();
}
