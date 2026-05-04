package com.connectsphere.auth.controller;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AuthResource.java — Authentication REST Controller
 *
 * BUG-FIX: Added 4 missing OTP endpoints that the frontend was calling:
 *   POST /auth/otp/login/request    — send OTP to email for login
 *   POST /auth/otp/login/verify     — verify OTP and return JWT
 *   POST /auth/otp/register/request — send OTP to email for registration
 *   POST /auth/otp/register/verify  — verify OTP and create account + return JWT
 */
@RestController
@RequestMapping("/auth")
public class AuthResource {

    private final AuthService authService;

    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    // ── Password-based Auth ───────────────────────────────────────────────────

    @PostMapping("/guest")
    public ResponseEntity<Map<String, String>> guestLogin() {
        return ResponseEntity.ok(authService.guestLogin());
    }

    @PostMapping("/register")
    public ResponseEntity<User> register(@RequestBody Map<String, String> body) {
        String fullName = body.get("fullName");
        if (fullName != null && !fullName.isEmpty()) {
            return ResponseEntity.ok(authService.registerWithFullName(
                body.get("username"), body.get("email"), body.get("password"), fullName));
        }
        return ResponseEntity.ok(authService.register(
                body.get("username"), body.get("email"), body.get("password")));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.login(body.get("email"), body.get("password")));
    }

    // ── OTP Login ─────────────────────────────────────────────────────────────
    //
    // FIX: These two endpoints were completely missing. The frontend Login.jsx
    // calls them but they returned 404, causing "OTP not found" errors.

    /**
     * POST /auth/otp/login/request
     * Body: { "email": "user@example.com" }
     *
     * Generates a 6-digit OTP and emails it to the user.
     * OTP is valid for 5 minutes.
     */
    @PostMapping("/otp/login/request")
    public ResponseEntity<String> requestLoginOtp(@RequestBody Map<String, String> body) {
        authService.requestLoginOtp(body.get("email"));
        return ResponseEntity.ok("OTP sent to your email");
    }

    /**
     * POST /auth/otp/login/verify
     * Body: { "email": "user@example.com", "otp": "123456" }
     *
     * Validates the OTP and returns a JWT token + user info on success.
     */
    @PostMapping("/otp/login/verify")
    public ResponseEntity<Map<String, String>> verifyLoginOtp(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.verifyLoginOtp(body.get("email"), body.get("otp")));
    }

    // ── OTP Registration ──────────────────────────────────────────────────────
    //
    // FIX: These two endpoints were completely missing. The frontend Register.jsx
    // uses OTP-based registration by default — without these the signup flow
    // always failed with a 404.

    /**
     * POST /auth/otp/register/request
     * Body: { "username": "...", "email": "...", "password": "...", "fullName": "..." }
     *
     * Validates the registration data and sends a verification OTP.
     * The user account is NOT created yet — only after verify succeeds.
     */
    @PostMapping("/otp/register/request")
    public ResponseEntity<String> requestRegisterOtp(@RequestBody Map<String, String> body) {
        authService.requestRegisterOtp(
                body.get("username"),
                body.get("email"),
                body.get("password"),
                body.get("fullName"));
        return ResponseEntity.ok("OTP sent to your email");
    }

    /**
     * POST /auth/otp/register/verify
     * Body: { "email": "user@example.com", "otp": "123456" }
     *
     * Validates the OTP, creates the user account, and returns a JWT.
     */
    @PostMapping("/otp/register/verify")
    public ResponseEntity<Map<String, String>> verifyRegisterOtp(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.verifyRegisterOtp(body.get("email"), body.get("otp")));
    }

    // ── Password Reset ────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body.get("email"));
        return ResponseEntity.ok("Reset link sent to your email");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("token"), body.get("newPassword"));
        return ResponseEntity.ok("Password reset successfully");
    }

    // ── User Info ─────────────────────────────────────────────────────────────

    @GetMapping("/user/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String query) {
        return ResponseEntity.ok(authService.searchUsers(query));
    }

    @GetMapping("/profile")
    public ResponseEntity<User> profile(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(authService.getProfile(email));
    }

    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(@AuthenticationPrincipal String email,
                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.updateProfile(
                email, body.get("bio"), body.get("fullName"),
                body.get("profilePicture"), body.get("coverPicture")));
    }

    @PostMapping("/user/{userId}/report")
    public ResponseEntity<Void> reportUser(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        authService.reportUser(userId, body.get("reason"));
        return ResponseEntity.ok().build();
    }

    @PutMapping("/user/{userId}/verify")
    public ResponseEntity<Void> verifyUser(@PathVariable Long userId) {
        authService.verifyUser(userId);
        return ResponseEntity.ok().build();
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @GetMapping("/admin/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(authService.getAnalytics());
    }

    @PutMapping("/admin/users/{userId}/role")
    public ResponseEntity<User> changeRole(@PathVariable Long userId, @RequestParam String role) {
        return ResponseEntity.ok(authService.changeRole(userId, role));
    }

    @PutMapping("/admin/users/{userId}/active")
    public ResponseEntity<User> toggleActive(@PathVariable Long userId, @RequestParam boolean active) {
        return ResponseEntity.ok(authService.toggleActive(userId, active));
    }

    @DeleteMapping("/admin/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        authService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}