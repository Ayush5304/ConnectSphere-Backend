package com.connectsphere.auth.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role = Role.USER;

    private boolean isActive = true;

    private String bio;
    private String profilePicture;
    private String coverPicture;
    private String fullName;

    private String resetToken;
    private java.time.LocalDateTime resetTokenExpiry;
    private java.time.LocalDateTime lastLoginAt;

    private boolean reported = false;
    private String reportReason;
    private boolean verified = false;

    // ── OTP fields for login-via-OTP ──────────────────────────────────────────
    /** 6-digit OTP for email-based login */
    private String loginOtp;

    /** When the login OTP expires (5 minutes from generation) */
    private java.time.LocalDateTime loginOtpExpiry;
    // ─────────────────────────────────────────────────────────────────────────

    public enum Role { GUEST, USER, ADMIN }

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
    public void setResetTokenExpiry(java.time.LocalDateTime v) { this.resetTokenExpiry = v; }
    public java.time.LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(java.time.LocalDateTime v) { this.lastLoginAt = v; }
    public boolean isReported() { return reported; }
    public void setReported(boolean reported) { this.reported = reported; }
    public String getReportReason() { return reportReason; }
    public void setReportReason(String r) { this.reportReason = r; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public String getLoginOtp() { return loginOtp; }
    public void setLoginOtp(String otp) { this.loginOtp = otp; }
    public java.time.LocalDateTime getLoginOtpExpiry() { return loginOtpExpiry; }
    public void setLoginOtpExpiry(java.time.LocalDateTime v) { this.loginOtpExpiry = v; }
}