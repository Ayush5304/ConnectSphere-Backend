/**
 * AuthResource.java — Authentication REST Controller
 *
 * This is the entry point for all HTTP requests to auth-service.
 * It receives requests, calls AuthService to do the work,
 * and returns the response.
 *
 * Think of it as a receptionist:
 *   - Receives the request
 *   - Passes it to the right department (AuthService)
 *   - Returns the result to the caller
 *
 * @RestController — combines @Controller + @ResponseBody
 *   Automatically converts return values to JSON
 *
 * @RequestMapping("/auth") — all endpoints in this class start with /auth
 *   e.g. /auth/login, /auth/register, /auth/profile
 */

package com.connectsphere.auth.controller;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthResource {

    /** authService — contains all the business logic */
    private final AuthService authService;

    /**
     * Constructor injection — Spring automatically provides AuthService
     * Better than @Autowired because it makes dependencies explicit
     */
    public AuthResource(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /auth/guest — Guest Login
     *
     * Creates a temporary JWT token with GUEST role.
     * No credentials needed — anyone can browse as guest.
     * Guest users cannot post, like, comment, or follow.
     *
     * Returns: Map with token, userId=0, username=guest, role=GUEST
     */
    @PostMapping("/guest")
    public ResponseEntity<Map<String, String>> guestLogin() {
        return ResponseEntity.ok(authService.guestLogin());
    }

    /**
     * POST /auth/forgot-password — Request Password Reset Email
     *
     * @RequestBody Map<String, String> body — receives JSON: {"email": "user@example.com"}
     * body.get("email") — extracts the email from the JSON body
     *
     * Sends a password reset link to the user's email.
     * Returns success message (even if email not found, for security)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody Map<String, String> body) {
        authService.forgotPassword(body.get("email"));
        return ResponseEntity.ok("Reset link sent to your email");
    }

    /**
     * POST /auth/reset-password — Reset Password with Token
     *
     * Receives: {"token": "uuid-from-email", "newPassword": "newpass123"}
     * Validates the token, checks it's not expired, updates password.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> body) {
        authService.resetPassword(body.get("token"), body.get("newPassword"));
        return ResponseEntity.ok("Password reset successfully");
    }

    /**
     * POST /auth/register — Register New User
     *
     * Receives: {"username": "khushi", "email": "k@gmail.com",
     *            "password": "pass123", "fullName": "Khushi Pathak"}
     *
     * Checks if fullName is provided → calls registerWithFullName
     * Otherwise → calls register (without fullName)
     *
     * Returns: The saved User object as JSON
     */
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

    /**
     * POST /auth/login — User Login
     *
     * Receives: {"email": "k@gmail.com", "password": "pass123"}
     * Returns: Map with token, userId, username, role, email, fullName, bio, profilePicture
     *
     * The token is a JWT that the frontend stores and sends with every request.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.login(body.get("email"), body.get("password")));
    }

    /**
     * GET /auth/user/{userId} — Get User by ID (Public)
     *
     * @PathVariable Long userId — extracts userId from the URL
     * e.g. GET /auth/user/5 → userId = 5
     *
     * Used by other services (follow-service, post-service) to get user info.
     * Also used by frontend to load profile data.
     * No authentication required — public endpoint.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<User> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(authService.getUserById(userId));
    }

    /**
     * GET /auth/search?query=khushi — Search Users
     *
     * @RequestParam String query — reads "query" from URL parameters
     * e.g. GET /auth/search?query=khushi
     *
     * Searches by username and fullName (case-insensitive).
     * Used by search-service to find users matching a search query.
     */
    @GetMapping("/search")
    public ResponseEntity<List<User>> searchUsers(@RequestParam String query) {
        return ResponseEntity.ok(authService.searchUsers(query));
    }

    /**
     * GET /auth/admin/users — Get All Users (Admin Only)
     *
     * Protected endpoint — requires valid JWT with ADMIN role.
     * Returns list of all registered users.
     * Used by AdminDashboard to manage users.
     */
    @GetMapping("/admin/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    /**
     * GET /auth/admin/analytics — Get User Statistics (Admin Only)
     *
     * Returns: {totalUsers, activeUsers, adminCount, guestCount, dailyActiveUsers}
     * Used by AdminDashboard analytics tab.
     */
    @GetMapping("/admin/analytics")
    public ResponseEntity<Map<String, Object>> analytics() {
        return ResponseEntity.ok(authService.getAnalytics());
    }

    /**
     * PUT /auth/admin/users/{userId}/role?role=ADMIN — Change User Role (Admin Only)
     *
     * @PathVariable Long userId — which user to update
     * @RequestParam String role — new role (USER/ADMIN/GUEST) from URL query param
     * e.g. PUT /auth/admin/users/5/role?role=ADMIN
     */
    @PutMapping("/admin/users/{userId}/role")
    public ResponseEntity<User> changeRole(@PathVariable Long userId, @RequestParam String role) {
        return ResponseEntity.ok(authService.changeRole(userId, role));
    }

    /**
     * PUT /auth/admin/users/{userId}/active?active=false — Suspend/Activate User (Admin Only)
     *
     * active=true  → activate the account
     * active=false → suspend the account (user cannot login)
     */
    @PutMapping("/admin/users/{userId}/active")
    public ResponseEntity<User> toggleActive(@PathVariable Long userId, @RequestParam boolean active) {
        return ResponseEntity.ok(authService.toggleActive(userId, active));
    }

    /**
     * DELETE /auth/admin/users/{userId} — Delete User Permanently (Admin Only)
     *
     * ResponseEntity.noContent().build() — returns HTTP 204 (No Content)
     * 204 means success but no data to return
     */
    @DeleteMapping("/admin/users/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        authService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /auth/profile — Get Logged-in User's Profile (Protected)
     *
     * @AuthenticationPrincipal String email — Spring Security automatically
     * injects the email of the currently authenticated user.
     * This email was set in JwtFilter when the token was validated.
     *
     * No need to pass userId — we know who you are from your JWT token.
     */
    @GetMapping("/profile")
    public ResponseEntity<User> profile(@AuthenticationPrincipal String email) {
        return ResponseEntity.ok(authService.getProfile(email));
    }

    /**
     * PUT /auth/profile — Update Profile (Protected)
     *
     * @AuthenticationPrincipal String email — identifies the logged-in user
     * @RequestBody — receives: {bio, fullName, profilePicture}
     *
     * Only updates fields that are provided (null fields are ignored).
     */
    @PutMapping("/profile")
    public ResponseEntity<User> updateProfile(@AuthenticationPrincipal String email,
                                               @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.updateProfile(
                email, body.get("bio"), body.get("fullName"),
                body.get("profilePicture"), body.get("coverPicture")));
    }

    /**
     * POST /auth/user/{userId}/report — Report a User Account
     *
     * Receives: {"reason": "This account is spamming"}
     * Sets reported=true and saves the reason on the user record.
     * Admin can see reported users in the dashboard.
     *
     * Returns HTTP 200 OK with no body (Void)
     */
    @PostMapping("/user/{userId}/report")
    public ResponseEntity<Void> reportUser(@PathVariable Long userId, @RequestBody Map<String, String> body) {
        authService.reportUser(userId, body.get("reason"));
        return ResponseEntity.ok().build();
    }

    /** PUT /auth/user/{userId}/verify — Called by payment-service after successful VERIFIED_BADGE payment */
    @PutMapping("/user/{userId}/verify")
    public ResponseEntity<Void> verifyUser(@PathVariable Long userId) {
        authService.verifyUser(userId);
        return ResponseEntity.ok().build();
    }
}
