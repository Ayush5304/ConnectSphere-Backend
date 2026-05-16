package com.connectsphere.auth;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.exception.BadRequestException;
import com.connectsphere.auth.exception.ResourceNotFoundException;
import com.connectsphere.auth.repository.UserRepository;
import com.connectsphere.auth.security.JwtUtil;
import com.connectsphere.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthServiceTest — Unit tests for AuthService.
 * Uses Mockito to mock all dependencies.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtil jwtUtil;
    @Mock JavaMailSender mailSender;
    @InjectMocks AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(authService, "fromEmail", "test@connectsphere.com");
    }

    /* ── register() tests ──────────────────────────────────────────── */

    @Test
    void register_success_savesUserAndReturns() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setUserId(1L);
            return u;
        });

        User result = authService.register("testuser", "test@test.com", "pass123");

        assertEquals("testuser", result.getUsername());
        assertEquals("test@test.com", result.getEmail());
        assertEquals("hashed", result.getPasswordHash());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_blankUsername_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.register("", "test@test.com", "pass123"));
    }

    @Test
    void register_blankEmail_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.register("testuser", "", "pass123"));
    }

    @Test
    void register_shortPassword_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.register("testuser", "test@test.com", "abc"));
    }

    @Test
    void register_duplicateEmail_throwsBadRequestException() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);
        assertThrows(BadRequestException.class,
            () -> authService.register("testuser", "test@test.com", "pass123"));
        verify(userRepository, never()).save(any());
    }


    /* ── login() tests ─────────────────────────────────────────────── */

    @Test
    void login_success_returnsTokenAndUserInfo() {
        User user = new User();
        user.setUserId(1L);
        user.setEmail("test@test.com");
        user.setUsername("testuser");
        user.setPasswordHash("hashed");
        user.setRole(User.Role.USER);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
        when(userRepository.save(any())).thenReturn(user);
        when(jwtUtil.generateToken("test@test.com", "USER")).thenReturn("jwt-token");

        Map<String, String> result = authService.login("test@test.com", "pass123");

        assertEquals("jwt-token", result.get("token"));
        assertEquals("testuser", result.get("username"));
        assertEquals("USER", result.get("role"));
    }

    @Test
    void login_blankEmail_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.login("", "pass123"));
    }

    @Test
    void login_blankPassword_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.login("test@test.com", ""));
    }

    @Test
    void login_wrongPassword_throwsBadRequestException() {
        User user = new User();
        user.setEmail("test@test.com");
        user.setPasswordHash("hashed");
        user.setRole(User.Role.USER);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThrows(BadRequestException.class,
            () -> authService.login("test@test.com", "wrongpass"));
    }

    @Test
    void login_userNotFound_throwsBadRequestException() {
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class,
            () -> authService.login("notfound@test.com", "pass123"));
    }

    @Test
    void login_suspendedAccount_throwsBadRequestException() {
        User user = new User();
        user.setEmail("test@test.com");
        user.setPasswordHash("hashed");
        user.setRole(User.Role.USER);
        user.setActive(false);

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);

        assertThrows(BadRequestException.class,
            () -> authService.login("test@test.com", "pass123"));
    }

    /* ── guestLogin() tests ────────────────────────────────────────── */

    @Test
    void guestLogin_returnsGuestTokenAndRole() {
        when(jwtUtil.generateToken("guest@connectsphere.com", "GUEST")).thenReturn("guest-token");

        Map<String, String> result = authService.guestLogin();

        assertEquals("guest-token", result.get("token"));
        assertEquals("GUEST", result.get("role"));
        assertEquals("guest", result.get("username"));
        assertEquals("0", result.get("userId"));
    }

    /* ── getUserById() tests ───────────────────────────────────────── */

    @Test
    void getUserById_found_returnsUser() {
        User user = new User();
        user.setUserId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = authService.getUserById(1L);

        assertEquals(1L, result.getUserId());
    }

    @Test
    void getUserById_notFound_throwsResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.getUserById(99L));
    }

    /* ── changeRole() tests ────────────────────────────────────────── */

    @Test
    void changeRole_success_updatesRole() {
        User user = new User();
        user.setUserId(1L);
        user.setRole(User.Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = authService.changeRole(1L, "ADMIN");

        assertEquals(User.Role.ADMIN, result.getRole());
    }

    @Test
    void changeRole_blankRole_throwsBadRequestException() {
        assertThrows(BadRequestException.class, () -> authService.changeRole(1L, ""));
    }

    /* ── resetPassword() tests ─────────────────────────────────────── */

    @Test
    void resetPassword_blankToken_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.resetPassword("", "newpass123"));
    }

    @Test
    void resetPassword_shortPassword_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.resetPassword("valid-token", "abc"));
    }

    /* ── reportUser() tests ────────────────────────────────────────── */

    @Test
    void reportUser_blankReason_throwsBadRequestException() {
        assertThrows(BadRequestException.class,
            () -> authService.reportUser(1L, ""));
    }

    @Test
    void requestLoginOtp_success_savesOtpAndSendsMail() {
        User user = user(7L, "otp@test.com", "otpuser");
        when(userRepository.findByEmail("otp@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        authService.requestLoginOtp("otp@test.com");

        assertNotNull(user.getLoginOtp());
        assertNotNull(user.getLoginOtpExpiry());
        verify(userRepository).save(user);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void requestLoginOtp_invalidCases_throwBadRequestException() {
        assertThrows(BadRequestException.class, () -> authService.requestLoginOtp(" "));
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> authService.requestLoginOtp("missing@test.com"));

        User inactive = user(8L, "off@test.com", "off");
        inactive.setActive(false);
        when(userRepository.findByEmail("off@test.com")).thenReturn(Optional.of(inactive));
        assertThrows(BadRequestException.class, () -> authService.requestLoginOtp("off@test.com"));
    }

    @Test
    void verifyLoginOtp_success_clearsOtpAndReturnsLoginResponse() {
        User user = user(9L, "otp@test.com", "otpuser");
        user.setLoginOtp("123456");
        user.setLoginOtpExpiry(LocalDateTime.now().plusMinutes(3));
        when(userRepository.findByEmail("otp@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtUtil.generateToken("otp@test.com", "USER")).thenReturn("otp-token");

        Map<String, String> result = authService.verifyLoginOtp("otp@test.com", " 123456 ");

        assertEquals("otp-token", result.get("token"));
        assertNull(user.getLoginOtp());
        assertNull(user.getLoginOtpExpiry());
    }

    @Test
    void verifyLoginOtp_invalidCases_throwBadRequestException() {
        assertThrows(BadRequestException.class, () -> authService.verifyLoginOtp("", "123456"));
        assertThrows(BadRequestException.class, () -> authService.verifyLoginOtp("a@test.com", ""));
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> authService.verifyLoginOtp("missing@test.com", "123456"));

        User noOtp = user(10L, "no@test.com", "no");
        when(userRepository.findByEmail("no@test.com")).thenReturn(Optional.of(noOtp));
        assertThrows(BadRequestException.class, () -> authService.verifyLoginOtp("no@test.com", "123456"));

        User expired = user(11L, "expired@test.com", "expired");
        expired.setLoginOtp("123456");
        expired.setLoginOtpExpiry(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail("expired@test.com")).thenReturn(Optional.of(expired));
        assertThrows(BadRequestException.class, () -> authService.verifyLoginOtp("expired@test.com", "123456"));

        User wrong = user(12L, "wrong@test.com", "wrong");
        wrong.setLoginOtp("123456");
        wrong.setLoginOtpExpiry(LocalDateTime.now().plusMinutes(1));
        when(userRepository.findByEmail("wrong@test.com")).thenReturn(Optional.of(wrong));
        assertThrows(BadRequestException.class, () -> authService.verifyLoginOtp("wrong@test.com", "000000"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void requestAndVerifyRegisterOtp_success_createsUserAndCleansPendingState() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        authService.requestRegisterOtp("newuser", "new@test.com", "pass123", "New User");

        ConcurrentHashMap<String, String> pendingOtps =
            (ConcurrentHashMap<String, String>) ReflectionTestUtils.getField(authService, "pendingOtps");
        String otp = pendingOtps.get("new@test.com");

        when(passwordEncoder.encode("pass123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setUserId(77L);
            return u;
        });
        when(jwtUtil.generateToken("new@test.com", "USER")).thenReturn("new-token");

        Map<String, String> response = authService.verifyRegisterOtp("new@test.com", otp);

        assertEquals("new-token", response.get("token"));
        assertFalse(pendingOtps.containsKey("new@test.com"));
    }

    @Test
    void requestRegisterOtp_invalidCases_throwBadRequestException() {
        assertThrows(BadRequestException.class, () -> authService.requestRegisterOtp("", "a@test.com", "pass123", ""));
        assertThrows(BadRequestException.class, () -> authService.requestRegisterOtp("user", "", "pass123", ""));
        assertThrows(BadRequestException.class, () -> authService.requestRegisterOtp("user", "a@test.com", "123", ""));
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> authService.requestRegisterOtp("user", "taken@test.com", "pass123", ""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyRegisterOtp_invalidCases_throwBadRequestException() {
        assertThrows(BadRequestException.class, () -> authService.verifyRegisterOtp("", "123456"));
        assertThrows(BadRequestException.class, () -> authService.verifyRegisterOtp("a@test.com", ""));
        assertThrows(BadRequestException.class, () -> authService.verifyRegisterOtp("none@test.com", "123456"));

        ConcurrentHashMap<String, Map<String, String>> registrations =
            (ConcurrentHashMap<String, Map<String, String>>) ReflectionTestUtils.getField(authService, "pendingRegistrations");
        ConcurrentHashMap<String, String> otps =
            (ConcurrentHashMap<String, String>) ReflectionTestUtils.getField(authService, "pendingOtps");
        ConcurrentHashMap<String, LocalDateTime> expiries =
            (ConcurrentHashMap<String, LocalDateTime>) ReflectionTestUtils.getField(authService, "pendingOtpExpiry");
        registrations.put("bad@test.com", Map.of("username", "bad", "email", "bad@test.com", "password", "pass123", "fullName", ""));
        otps.put("bad@test.com", "111111");
        expiries.put("bad@test.com", LocalDateTime.now().plusMinutes(1));
        assertThrows(BadRequestException.class, () -> authService.verifyRegisterOtp("bad@test.com", "222222"));

        registrations.put("expired-reg@test.com", Map.of("username", "bad", "email", "expired-reg@test.com", "password", "pass123", "fullName", ""));
        otps.put("expired-reg@test.com", "111111");
        expiries.put("expired-reg@test.com", LocalDateTime.now().minusMinutes(1));
        assertThrows(BadRequestException.class, () -> authService.verifyRegisterOtp("expired-reg@test.com", "111111"));

        registrations.put("dupe-reg@test.com", Map.of("username", "bad", "email", "dupe-reg@test.com", "password", "pass123", "fullName", ""));
        otps.put("dupe-reg@test.com", "111111");
        expiries.put("dupe-reg@test.com", LocalDateTime.now().plusMinutes(1));
        when(userRepository.existsByEmail("dupe-reg@test.com")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> authService.verifyRegisterOtp("dupe-reg@test.com", "111111"));
    }

    @Test
    void login_configuredAdmin_bootstrapsAdminBeforeLogin() {
        ReflectionTestUtils.setField(authService, "configuredAdminEmail", "admin@test.com");
        ReflectionTestUtils.setField(authService, "configuredAdminPassword", "adminPass");
        ReflectionTestUtils.setField(authService, "configuredAdminUsername", "root");
        ReflectionTestUtils.setField(authService, "configuredAdminFullName", "Root Admin");
        User admin = user(99L, "admin@test.com", "root");
        admin.setRole(User.Role.ADMIN);
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.empty(), Optional.of(admin));
        when(passwordEncoder.encode("adminPass")).thenReturn("encoded-admin");
        when(passwordEncoder.matches("adminPass", "hashed")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(admin);
        when(jwtUtil.generateToken("admin@test.com", "ADMIN")).thenReturn("admin-token");

        Map<String, String> response = authService.login("admin@test.com", "adminPass");

        assertEquals("ADMIN", response.get("role"));
        assertEquals("admin-token", response.get("token"));
    }

    @Test
    void searchUsers_getAllUsers_andAnalytics_returnRepositoryData() {
        User ayush = user(1L, "a@test.com", "ayushmishra1");
        ayush.setFullName("Ayush Mishra");
        User harsh = user(2L, "h@test.com", "harsh");
        when(userRepository.findAll()).thenReturn(List.of(ayush, harsh));
        assertEquals(2, authService.getAllUsers().size());
        assertEquals(1, authService.searchUsers("mishra").size());
        assertThrows(BadRequestException.class, () -> authService.searchUsers(" "));

        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByIsActiveTrue()).thenReturn(8L);
        when(userRepository.countByRole(User.Role.ADMIN)).thenReturn(1L);
        when(userRepository.countByRole(User.Role.GUEST)).thenReturn(2L);
        when(userRepository.countByLastLoginAtAfter(any())).thenReturn(6L);
        Map<String, Object> analytics = authService.getAnalytics();
        assertEquals(10L, analytics.get("totalUsers"));
        assertEquals(6L, analytics.get("dailyActiveUsers"));
    }

    @Test
    void forgotAndResetPassword_successAndFailures() {
        User user = user(5L, "reset@test.com", "reset");
        when(userRepository.findByEmail("reset@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        authService.forgotPassword("reset@test.com");

        assertNotNull(user.getResetToken());
        assertThrows(BadRequestException.class, () -> authService.forgotPassword(""));
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.forgotPassword("none@test.com"));

        user.setResetToken("token");
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByResetToken("token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");
        authService.resetPassword("token", "newpass");
        assertNull(user.getResetToken());
        assertEquals("new-hash", user.getPasswordHash());

        when(userRepository.findByResetToken("bad")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> authService.resetPassword("bad", "newpass"));

        User expired = user(6L, "expired@test.com", "expired");
        expired.setResetTokenExpiry(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByResetToken("expired")).thenReturn(Optional.of(expired));
        assertThrows(BadRequestException.class, () -> authService.resetPassword("expired", "newpass"));
    }

    @Test
    void profileAndAdminActions_updateExpectedFields() {
        User user = user(15L, "profile@test.com", "oldname");
        when(userRepository.findByEmail("profile@test.com")).thenReturn(Optional.of(user));
        when(userRepository.findById(15L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User profile = authService.getProfile("profile@test.com");
        assertEquals("oldname", profile.getUsername());

        User updated = authService.updateProfile("profile@test.com", "  hello  ", " Full Name ", " New Name ", "", "/cover.png", "true");
        assertEquals("hello", updated.getBio());
        assertEquals("newname", updated.getUsername());
        assertNull(updated.getProfilePicture());
        assertTrue(updated.isPrivateAccount());
        assertThrows(BadRequestException.class, () -> authService.updateProfile("profile@test.com", "x".repeat(151), null, null, null, null, null));

        User byId = authService.updateProfileByUserId(15L, "bio", "Name", "Another User", "/p.png", "", "false");
        assertEquals("anotheruser", byId.getUsername());
        assertFalse(byId.isPrivateAccount());
        assertThrows(BadRequestException.class, () -> authService.updateProfileByUserId(15L, "x".repeat(151), null, null, null, null, null));

        authService.toggleActive(15L, false);
        assertFalse(user.isActive());
        authService.reportUser(15L, "spam");
        assertTrue(user.isReported());
        assertEquals("spam", user.getReportReason());
        authService.clearUserReport(15L);
        assertFalse(user.isReported());
        assertNull(user.getReportReason());
        authService.verifyUser(15L);
        assertTrue(user.isVerified());
        authService.deleteUser(15L);
        verify(userRepository).deleteById(15L);
    }

    @Test
    void reportedUsersAndVerifyByEmail_coverRepositoryBranches() {
        User reported = user(17L, "reported@test.com", "reported");
        when(userRepository.findByReportedTrue()).thenReturn(List.of(reported));
        assertEquals(1, authService.getReportedUsers().size());

        User user = user(18L, "verify@test.com", "verify");
        when(userRepository.findByEmail("verify@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        authService.verifyUserByEmail("verify@test.com");
        assertTrue(user.isVerified());

        assertThrows(BadRequestException.class, () -> authService.verifyUserByEmail(""));
        when(userRepository.findByEmail("none-verify@test.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> authService.verifyUserByEmail("none-verify@test.com"));
    }

    private User user(Long id, String email, String username) {
        User user = new User();
        user.setUserId(id);
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash("hashed");
        user.setRole(User.Role.USER);
        user.setActive(true);
        return user;
    }

}
