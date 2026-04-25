/**
 * User.java — User Entity (Database Table Mapping)
 *
 * This class represents the "users" table in the authdb database.
 * Every field in this class = one column in the database table.
 *
 * @Entity  — tells Hibernate this class maps to a database table
 * @Table   — specifies the table name is "users"
 *
 * Hibernate automatically creates/updates this table on startup
 * because of ddl-auto: update in application.yml
 *
 * This class is used by:
 *   - AuthService  — to create, find, update users
 *   - JwtFilter    — to load user details for authentication
 *   - AuthResource — to return user data in API responses
 */

package com.connectsphere.auth.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    /**
     * userId — Primary Key (auto-incremented by database)
     * @Id — marks this as the primary key
     * @GeneratedValue(IDENTITY) — database auto-generates the ID (1, 2, 3...)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    /**
     * username — Unique username for the user (e.g. "khushipathak")
     * unique = true  — no two users can have the same username
     * nullable = false — username is required, cannot be empty
     */
    @Column(unique = true, nullable = false)
    private String username;

    /**
     * email — User's email address (e.g. "khushi@gmail.com")
     * unique = true  — no two users can have the same email
     * nullable = false — email is required
     */
    @Column(unique = true, nullable = false)
    private String email;

    /**
     * passwordHash — BCrypt hashed password
     * We NEVER store plain text passwords
     * BCrypt converts "mypassword" → "$2a$10$randomhash..."
     * Even if database is hacked, passwords cannot be reversed
     */
    private String passwordHash;

    /**
     * role — User's permission level
     * @Enumerated(STRING) — stores the enum name as string in DB ("USER", "ADMIN", "GUEST")
     * Default is USER for all new registrations
     *
     * GUEST  — can only browse, cannot post/like/comment
     * USER   — normal registered user, full access
     * ADMIN  — can manage all users, posts, comments
     */
    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    /**
     * isActive — Whether the account is active or suspended
     * true  = account is active (can login)
     * false = account is suspended (cannot login, admin blocked them)
     * Default is true for all new accounts
     */
    private boolean isActive = true;

    /** bio — Short description about the user (optional) */
    private String bio;

    /** profilePicture — URL of the user's profile picture (stored in media-service) */
    private String profilePicture;

    /** coverPicture — URL of the user's cover/banner photo */
    private String coverPicture;

    /** fullName — User's full name (e.g. "Khushi Pathak") */
    private String fullName;

    /**
     * resetToken — UUID token generated for password reset
     * Generated when user clicks "Forgot Password"
     * Sent to user's email as part of reset link
     * Cleared after password is successfully reset
     */
    private String resetToken;

    /**
     * resetTokenExpiry — When the reset token expires
     * Set to 1 hour from when it was generated
     * If user tries to reset after expiry → error "Token has expired"
     */
    private java.time.LocalDateTime resetTokenExpiry;

    /**
     * lastLoginAt — Timestamp of the user's last login
     * Updated every time user successfully logs in
     * Used in admin analytics to count "daily active users"
     */
    private java.time.LocalDateTime lastLoginAt;

    /**
     * reported — Whether this user has been reported by another user
     * false = not reported (default)
     * true  = someone reported this account
     */
    private boolean reported = false;

    /** reportReason — The reason given when reporting this user */
    private String reportReason;

    /** verified — Whether user has paid for verified badge (blue tick) */
    private boolean verified = false;

    /**
     * Role enum — defines the 3 possible user roles
     * Stored as a string in the database column
     */
    public enum Role { GUEST, USER, ADMIN }

    /*
     * Getters and Setters — standard Java methods to read and write each field
     * Spring/Hibernate uses these to convert between Java objects and database rows
     * Jackson (JSON library) uses these to convert to/from JSON for API responses
     */
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }
    public String getCoverPicture() { return coverPicture; }
    public void setCoverPicture(String coverPicture) { this.coverPicture = coverPicture; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
    public java.time.LocalDateTime getResetTokenExpiry() { return resetTokenExpiry; }
    public void setResetTokenExpiry(java.time.LocalDateTime resetTokenExpiry) { this.resetTokenExpiry = resetTokenExpiry; }
    public java.time.LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(java.time.LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public boolean isReported() { return reported; }
    public void setReported(boolean reported) { this.reported = reported; }
    public String getReportReason() { return reportReason; }
    public void setReportReason(String reportReason) { this.reportReason = reportReason; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
