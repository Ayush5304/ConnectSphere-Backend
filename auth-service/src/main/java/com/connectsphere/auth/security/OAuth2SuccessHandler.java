package com.connectsphere.auth.security;

import com.connectsphere.auth.entity.User;
import com.connectsphere.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public OAuth2SuccessHandler(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name  = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        if (email == null) email = oAuth2User.getAttribute("login") + "@github.com";

        String finalEmail = email;
        String finalName  = name;
        String finalPicture = picture;

        User user = userRepository.findByEmail(finalEmail).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(finalEmail);
            // Generate unique username — append random suffix if base is taken
            String base = (finalName != null ? finalName : finalEmail)
                    .replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            if (base.isBlank()) base = "user";
            String username = base;
            int attempt = 0;
            while (userRepository.existsByUsername(username)) {
                username = base + (++attempt);
            }
            newUser.setUsername(username);
            newUser.setFullName(finalName);
            if (finalPicture != null) newUser.setProfilePicture(finalPicture);
            newUser.setPasswordHash("");
            newUser.setRole(User.Role.USER);
            return userRepository.save(newUser);
        });

        // Update profile picture from Google if not already set, then stamp lastLoginAt — single save
        boolean dirty = false;
        if (finalPicture != null && (user.getProfilePicture() == null || user.getProfilePicture().isBlank())) {
            user.setProfilePicture(finalPicture);
            dirty = true;
        }
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        String redirectUrl = frontendUrl + "/oauth2/callback"
                + "?token=" + token
                + "&userId=" + user.getUserId()
                + "&username=" + java.net.URLEncoder.encode(user.getUsername(), java.nio.charset.StandardCharsets.UTF_8)
                + "&role=" + user.getRole().name()
                + "&email=" + java.net.URLEncoder.encode(user.getEmail(), java.nio.charset.StandardCharsets.UTF_8)
                + "&fullName=" + java.net.URLEncoder.encode(user.getFullName() != null ? user.getFullName() : "", java.nio.charset.StandardCharsets.UTF_8)
                + "&profilePicture=" + java.net.URLEncoder.encode(user.getProfilePicture() != null ? user.getProfilePicture() : "", java.nio.charset.StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
