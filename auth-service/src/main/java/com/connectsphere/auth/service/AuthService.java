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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AuthService — Authentication and user management business logic.
 *
 * BUG-FIX: Added OTP-based login and OTP-based registration flows.
 *   - requestLoginOtp  / verifyLoginOtp   → OTP stored in User entity (loginOtp + loginOtpExpiry)
 *   - requestRegisterOtp / verifyRegisterOtp → pending data held in memory until OTP verified
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

    /**
     * Temporary in-memory store for pending OTP registrations.
     * Key = email, Value = registration data map.
     * Cleared after successful verification or after OTP expires.
     */
    private final ConcurrentHashMap<String, Map<String, String>> pendingRegistrations = new ConcurrentHashMap<>();

    /** OTP value mapped to its expiry for pending registrations (separate from User entity) */
    private final ConcurrentHashMap<String, String> pendingOtps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> pendingOtpExpiry = new ConcurrentHashMap<>();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.mailSender = mailSender;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Generates a cryptographically random 6-digit OTP string. */
    private String generateOtp() {
        SecureRandom rng = new SecureRandom();
        int code = 100_000 + rng.nextInt(900_000);
        return String.valueOf(code);
    }

    /** Sends a plain-text email. Failure is logged but not thrown. */
    private void sendMail(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Email send failed to {}: {}", to, e.getMessage());
        }
    }

    // ── OTP Login ─────────────────────────────────────────────────────────────

    /**
     * requestLoginOtp() — Generates a 6-digit OTP and emails it to the user.
     *
     * FIX: This endpoint was missing from the backend. The frontend calls
     * POST /auth/otp/login/request and expected this behaviour.
     *
     * The OTP is stored directly on the User entity in two new fields:
     *   loginOtp        — the 6-digit code
     *   loginOtpExpiry  — 5 minutes from now
     */
    public void requestLoginOtp(String email) {
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account found with this email."));

        if (!user.isActive())
            throw new BadRequestException("Account is suspended. Please contact support.");

        String otp = generateOtp();
        user.setLoginOtp(otp);
        user.setLoginOtpExpiry(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        log.info("Login OTP generated for email={}", email);

        sendMail(email,
                "ConnectSphere — Your Login OTP",
                "Hi " + user.getUsername() + ",\n\n"
                + "Your one-time login code is: " + otp + "\n\n"
                + "This code expires in 5 minutes.\n"
                + "If you did not request this, please ignore this email.\n\n"
                + "ConnectSphere Team");
    }

    /**
     * verifyLoginOtp() — Validates the submitted OTP and returns a JWT on success.
     *
     * FIX: This endpoint was missing from the backend. The frontend calls
     * POST /auth/otp/login/verify with {email, otp}.
     */
    public Map<String, String> verifyLoginOtp(String email, String otp) {
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");
        if (otp == null || otp.isBlank())
            throw new BadRequestException("OTP is required.");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("No account found with this email."));

        if (user.getLoginOtp() == null)
            throw new BadRequestException("No OTP requested. Please request an OTP first.");

        if (LocalDateTime.now().isAfter(user.getLoginOtpExpiry()))
            throw new BadRequestException("OTP has expired. Please request a new one.");

        if (!user.getLoginOtp().equals(otp.trim()))
            throw new BadRequestException("Invalid OTP. Please check and try again.");

        // Clear OTP after successful use (one-time use)
        user.setLoginOtp(null);
        user.setLoginOtpExpiry(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("OTP login successful for email={}", email);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return buildLoginResponse(user, token);
    }

    // ── OTP Registration ──────────────────────────────────────────────────────

    /**
     * requestRegisterOtp() — Validates registration data, stores it temporarily,
     * and sends a 6-digit OTP to the provided email.
     *
     * FIX: This endpoint was missing from the backend. The frontend calls
     * POST /auth/otp/register/request with {username, email, password, fullName}.
     *
     * The user is NOT created yet — only after verifyRegisterOtp succeeds.
     */
    public void requestRegisterOtp(String username, String email, String password, String fullName) {
        // Validate fields
        if (username == null || username.isBlank())
            throw new BadRequestException("Username is required.");
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");
        if (password == null || password.length() < 6)
            throw new BadRequestException("Password must be at least 6 characters.");

        // Check uniqueness before sending OTP
        if (userRepository.existsByEmail(email))
            throw new BadRequestException("Email is already in use.");
        if (userRepository.existsByUsername(username))
            throw new BadRequestException("Username is already taken.");

        String otp = generateOtp();

        // Store pending registration data in memory
        Map<String, String> regData = new HashMap<>();
        regData.put("username", username);
        regData.put("email", email);
        regData.put("password", password);
        regData.put("fullName", fullName != null ? fullName : "");
        pendingRegistrations.put(email, regData);
        pendingOtps.put(email, otp);
        pendingOtpExpiry.put(email, LocalDateTime.now().plusMinutes(10));

        log.info("Register OTP generated for email={}", email);

        sendMail(email,
                "ConnectSphere — Verify Your Email",
                "Hi " + (fullName != null && !fullName.isBlank() ? fullName : username) + ",\n\n"
                + "Your verification code is: " + otp + "\n\n"
                + "This code expires in 10 minutes.\n"
                + "If you did not create an account, please ignore this email.\n\n"
                + "ConnectSphere Team");
    }

    /**
     * verifyRegisterOtp() — Validates OTP, creates the user account, and returns JWT.
     *
     * FIX: This endpoint was missing from the backend. The frontend calls
     * POST /auth/otp/register/verify with {email, otp}.
     */
    public Map<String, String> verifyRegisterOtp(String email, String otp) {
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");
        if (otp == null || otp.isBlank())
            throw new BadRequestException("OTP is required.");

        String storedOtp = pendingOtps.get(email);
        LocalDateTime expiry = pendingOtpExpiry.get(email);
        Map<String, String> regData = pendingRegistrations.get(email);

        if (storedOtp == null || regData == null)
            throw new BadRequestException("No pending registration found. Please start over.");

        if (LocalDateTime.now().isAfter(expiry))
            throw new BadRequestException("OTP has expired. Please request a new one.");

        if (!storedOtp.equals(otp.trim()))
            throw new BadRequestException("Invalid OTP. Please check and try again.");

        // Re-check uniqueness in case someone registered in the meantime
        if (userRepository.existsByEmail(email))
            throw new BadRequestException("Email is already in use.");
        if (userRepository.existsByUsername(regData.get("username")))
            throw new BadRequestException("Username is already taken.");

        // Create the user
        User user = new User();
        user.setUsername(regData.get("username"));
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(regData.get("password")));
        String fullName = regData.get("fullName");
        if (fullName != null && !fullName.isBlank()) user.setFullName(fullName);
        user.setLastLoginAt(LocalDateTime.now());

        User saved = userRepository.save(user);

        // Clean up pending stores
        pendingRegistrations.remove(email);
        pendingOtps.remove(email);
        pendingOtpExpiry.remove(email);

        log.info("OTP registration successful: userId={}, email={}", saved.getUserId(), email);

        // Send welcome email (non-blocking)
        sendMail(email,
                "Welcome to ConnectSphere! 🎉",
                "Hi " + (fullName != null && !fullName.isBlank() ? fullName : saved.getUsername()) + ",\n\n"
                + "Your account is verified and ready. Welcome to ConnectSphere!\n\n"
                + "Visit: " + frontendUrl + "\n\nConnectSphere Team");

        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole().name());
        return buildLoginResponse(saved, token);
    }

    // ── Existing Methods (unchanged) ──────────────────────────────────────────

    public Map<String, String> guestLogin() {
        log.info("Guest login requested");
        String token = jwtUtil.generateToken("guest@connectsphere.com", "GUEST");
        return Map.of("token", token, "userId", "0", "username", "guest", "role", "GUEST");
    }

    public User register(String username, String email, String password) {
        return registerWithFullName(username, email, password, null);
    }

    public User registerWithFullName(String username, String email, String password, String fullName) {
        if (username == null || username.isBlank())
            throw new BadRequestException("Username is required.");
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");
        if (password == null || password.length() < 6)
            throw new BadRequestException("Password must be at least 6 characters.");

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
        log.info("User registered: userId={}", saved.getUserId());

        sendMail(email,
                "Welcome to ConnectSphere! 🎉",
                "Hi " + (fullName != null ? fullName : username) + ",\n\n"
                + "Welcome to ConnectSphere!\n\nVisit: " + frontendUrl + "\n\nConnectSphere Team");

        return saved;
    }

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

        log.info("Login successful: userId={}", user.getUserId());

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        return buildLoginResponse(user, token);
    }

    private Map<String, String> buildLoginResponse(User user, String token) {
        HashMap<String, String> map = new HashMap<>();
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

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    public List<User> getAllUsers() { return userRepository.findAll(); }

    public List<User> searchUsers(String query) {
        if (query == null || query.isBlank())
            throw new BadRequestException("Search query is required.");
        String q = query.toLowerCase();
        return userRepository.findAll().stream()
            .filter(u -> u.getUsername().toLowerCase().contains(q)
                || (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)))
            .collect(java.util.stream.Collectors.toList());
    }

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

    public void forgotPassword(String email) {
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required.");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("No account found with this email."));

        String token = UUID.randomUUID().toString();
        user.setResetToken(token);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        sendMail(email,
                "ConnectSphere — Reset Your Password",
                "Hi " + user.getUsername() + ",\n\n"
                + "Click the link to reset your password (valid 1 hour):\n\n"
                + frontendUrl + "/reset-password?token=" + token + "\n\n"
                + "If you did not request this, ignore this email.\n\nConnectSphere Team");

        log.info("Password reset email sent to {}", email);
    }

    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank())
            throw new BadRequestException("Reset token is required.");
        if (newPassword == null || newPassword.length() < 6)
            throw new BadRequestException("Password must be at least 6 characters.");

        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token."));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now()))
            throw new BadRequestException("Reset token has expired. Please request a new one.");

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        log.info("Password reset for userId={}", user.getUserId());
    }

    public User changeRole(Long userId, String role) {
        if (role == null || role.isBlank())
            throw new BadRequestException("Role is required.");
        User user = getUserById(userId);
        user.setRole(User.Role.valueOf(role));
        return userRepository.save(user);
    }

    public User toggleActive(Long userId, boolean active) {
        User user = getUserById(userId);
        user.setActive(active);
        return userRepository.save(user);
    }

    public void deleteUser(Long userId) { userRepository.deleteById(userId); }

    public User getProfile(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    public User updateProfile(String email, String bio, String fullName, String profilePicture, String coverPicture) {
        User user = getProfile(email);
        if (bio != null) user.setBio(bio);
        if (fullName != null) user.setFullName(fullName);
        if (profilePicture != null) user.setProfilePicture(profilePicture.isBlank() ? null : profilePicture);
        if (coverPicture != null) user.setCoverPicture(coverPicture.isBlank() ? null : coverPicture);
        return userRepository.save(user);
    }

    public void reportUser(Long userId, String reason) {
        if (reason == null || reason.isBlank())
            throw new BadRequestException("Report reason is required.");
        User user = getUserById(userId);
        user.setReported(true);
        user.setReportReason(reason);
        userRepository.save(user);
    }

    public void verifyUser(Long userId) {
        User user = getUserById(userId);
        user.setVerified(true);
        userRepository.save(user);
    }
}