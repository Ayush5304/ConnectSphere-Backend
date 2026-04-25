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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

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
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
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

    @Test
    void register_duplicateUsername_throwsBadRequestException() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(userRepository.existsByUsername("testuser")).thenReturn(true);
        assertThrows(BadRequestException.class,
            () -> authService.register("testuser", "test@test.com", "pass123"));
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
}
