package com.connectsphere.auth.service;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.exception.BadRequestException;
import com.connectsphere.auth.exception.ResourceNotFoundException;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AuthService — Authentication and user management business logic.
 *
 * Responsibilities:
 *   - Register new users (with validation + welcome email)
 *   - Login users (validate credentials + generate JWT)
 *   - Password reset (generate token + send email + validate + update)
 *   - Profile management (get + update)
 *   - Admin operations (list users, change roles, suspend, delete)
 *   - User reporting
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.mailSender = mailSender;
    }

    /**
     * guestLogin() — Creates a guest session without credentials.
     * Returns a JWT token with GUEST role and userId=0.
     */
    public Map<String, String> guestLogin() {
        log.info("Guest login requested");
        String token = jwtUtil.generateToken("guest@connectsphere.com", "GUEST");
        return Map.of("token", token, "userId", "0", "username", "guest", "role", "GUEST");
    }

    /** register() — Delegates to registerWithFullName with null fullName. */
    public User register(String username, String email, String password) {
        return registerWithFullName(username, email, password, null);
    }

    /**
     * registerWithFullName() — Registers a new user with full validation.
     *
     * Validates: username, email, password length, uniqueness.
     * Hashes password with BCrypt before saving.
     * Sends welcome email (failure does not block registration).
     */
    public User registerWithFullName(String username, String email, String password, String fullName) {
        /* Validate required fields */
        if (username == null || username.isBlank())
            throw new BadRequestException("Username is required.");
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");
        if (password == null || password.length() < 6)
            throw new BadRequestException("Password must be at least 6 characters.");

        /* Validate uniqueness */
        if (userRepository.existsByEmail(email))
            throw new BadRequestException("Email is already in use.");
        if (userRepository.existsByUsername(username))
            throw new BadRequestException("Username is already taken.");

        log.info("Registering new user: username={}, email={}", username, email);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        if (fullName != null && !fullName.isBlank()) user.setFullName(fullName);
        user.setPasswordHash(passwordEncoder.encode(password));

        User saved = userRepository.save(user);
        log.info("User registered successfully: userId={}, username={}", saved.getUserId(), username);

        /* Send welcome email — failure does not break registration */
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(fromEmail);
            mail.setTo(email);
            mail.setSubject("Welcome to ConnectSphere! 🎉");
            mail.setText("Hi " + (fullName != null ? fullName : username) + ",\n\n"
                    + "Welcome to ConnectSphere! Your account has been created successfully.\n\n"
                    + "Start sharing moments, connecting with people, and discovering trending content.\n\n"
                    + "Visit: " + frontendUrl + "\n\nConnectSphere Team");
            mailSender.send(mail);
        } catch (Exception e) {
            log.warn("Welcome email failed for {}: {}", email, e.getMessage());
        }

        return saved;
    }

    /**
     * login() — Validates credentials and returns JWT token + user info.
     *
     * Validates email and password presence, checks credentials,
     * verifies account is active, updates lastLoginAt, returns JWT.
     */
    public Map<String, String> login(String email, String password) {
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");
        if (password == null || password.isBlank())
            throw new BadRequestException("Password is required.");

        log.info("Login attempt for email={}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid email or password."));

        if (!passwordEncoder.matches(password, user.getPasswordHash()))
            throw new BadRequestException("Invalid email or password.");

        if (!user.isActive())
            throw new BadRequestException("Account is suspended. Please contact support.");

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Login successful for userId={}, username={}", user.getUserId(), user.getUsername());

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return buildLoginResponse(user, token);
    }

    /** buildLoginResponse() — Builds the login response map with all user fields. */
    private Map<String, String> buildLoginResponse(User user, String token) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        map.put("token", token);
        map.put("userId", user.getUserId().toString());
        map.put("username", user.getUsername());
        map.put("role", user.getRole().name());
        map.put("email", user.getEmail() != null ? user.getEmail() : "");
        map.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        map.put("bio", user.getBio() != null ? user.getBio() : "");
        map.put("profilePicture", user.getProfilePicture() != null ? user.getProfilePicture() : "");
        return map;
    }

    /**
     * getUserById() — Finds a user by their ID.
     * Throws ResourceNotFoundException if not found.
     */
    public User getUserById(Long userId) {
        log.debug("Fetching user by id={}", userId);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    /** getAllUsers() — Returns all users (admin dashboard). */
    public List<User> getAllUsers() {
        log.debug("Fetching all users");
        return userRepository.findAll();
    }

    /** searchUsers() — Searches users by username or fullName (case-insensitive). */
    public List<User> searchUsers(String query) {
        if (query == null || query.isBlank())
            throw new BadRequestException("Search query is required.");
        String q = query.toLowerCase();
        log.debug("Searching users with query={}", query);
        return userRepository.findAll().stream()
            .filter(u -> u.getUsername().toLowerCase().contains(q)
                || (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)))
            .collect(java.util.stream.Collectors.toList());
    }

    /** getAnalytics() — Returns user statistics for admin dashboard. */
    public Map<String, Object> getAnalytics() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByIsActiveTrue();
        long adminCount = userRepository.countByRole(User.Role.ADMIN);
        long guestCount = userRepository.countByRole(User.Role.GUEST);
        long dailyActiveUsers = userRepository.countByLastLoginAtAfter(LocalDateTime.now().minusHours(24));
        return Map.of("totalUsers", totalUsers, "activeUsers", activeUsers,
                      "adminCount", adminCount, "guestCount", guestCount,
                      "dailyActiveUsers", dailyActiveUsers);
    }

    /**
     * forgotPassword() — Generates a reset token and sends it via email.
     * Token expires in 1 hour.
     */
    public void forgotPassword(String email) {
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");

        log.info("Password reset requested for email={}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email."));

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromEmail);
        mail.setTo(email);
        mail.setSubject("ConnectSphere - Reset Your Password");
        mail.setText("Hi " + user.getUsername() + ",\n\n"
                + "Click the link below to reset your password (valid for 1 hour):\n\n"
                + frontendUrl + "/reset-password?token=" + token + "\n\n"
                + "If you did not request this, ignore this email.\n\nConnectSphere Team");
        mailSender.send(mail);
        log.info("Password reset email sent to {}", email);
    }

    /**
     * resetPassword() — Updates password using the reset token.
     * Validates token existence, expiry, and new password length.
     */
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank())
            throw new BadRequestException("Reset token is required.");
        if (newPassword == null || newPassword.length() < 6)
            throw new BadRequestException("Password must be at least 6 characters.");

        log.info("Password reset attempt with token");

        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token."));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now()))
            throw new BadRequestException("Reset token has expired. Please request a new one.");

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        log.info("Password reset successfully for userId={}", user.getUserId());
    }

    /** changeRole() — Changes a user's role (admin operation). */
    public User changeRole(Long userId, String role) {
        if (role == null || role.isBlank())
            throw new BadRequestException("Role is required.");
        log.info("Changing role for userId={} to {}", userId, role);
        User user = getUserById(userId);
        user.setRole(User.Role.valueOf(role));
        return userRepository.save(user);
    }

    /** toggleActive() — Suspends or activates a user account (admin operation). */
    public User toggleActive(Long userId, boolean active) {
        log.info("Setting active={} for userId={}", active, userId);
        User user = getUserById(userId);
        user.setActive(active);
        return userRepository.save(user);
    }

    /** deleteUser() — Permanently deletes a user (admin operation). */
    public void deleteUser(Long userId) {
        log.info("Deleting userId={}", userId);
        userRepository.deleteById(userId);
    }

    /** getProfile() — Gets user by email (for logged-in user's own profile). */
    public User getProfile(String email) {
        log.debug("Fetching profile for email={}", email);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    /**
     * updateProfile() — Updates user's profile fields.
     * Passing empty string for profilePicture removes the photo.
     * Passing null means "don't change this field".
     */
    public User updateProfile(String email, String bio, String fullName, String profilePicture, String coverPicture) {
        User user = getProfile(email);
        if (bio != null) user.setBio(bio);
        if (fullName != null) user.setFullName(fullName);
        if (profilePicture != null) user.setProfilePicture(profilePicture.isBlank() ? null : profilePicture);
        if (coverPicture != null) user.setCoverPicture(coverPicture.isBlank() ? null : coverPicture);
        log.info("Profile updated for email={}", email);
        return userRepository.save(user);
    }

    /** reportUser() — Marks a user as reported with a reason. */
    public void reportUser(Long userId, String reason) {
        if (reason == null || reason.isBlank())
            throw new BadRequestException("Report reason is required.");
        User user = getUserById(userId);
        user.setReported(true);
        user.setReportReason(reason);
        userRepository.save(user);
        log.info("User userId={} reported", userId);
    }

    /** verifyUser() — Sets verified=true on user (called by payment-service). */
    public void verifyUser(Long userId) {
        User user = getUserById(userId);
        user.setVerified(true);
        userRepository.save(user);
        log.info("User userId={} verified", userId);
    }
}
