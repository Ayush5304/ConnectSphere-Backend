/**
 * SecurityConfig.java — Spring Security Configuration
 *
 * This class configures WHO can access WHICH endpoints in auth-service.
 * Think of it as the rulebook for the security guard (JwtFilter).
 *
 * We have TWO separate security filter chains:
 *
 * Chain 1 (OAuth2 Chain) — handles Google login routes
 *   - Needs HTTP sessions (Google OAuth2 requires session to store state)
 *   - Only applies to /oauth2/** and /login/oauth2/** paths
 *
 * Chain 2 (API Chain) — handles all regular API routes
 *   - Stateless (no sessions — uses JWT tokens instead)
 *   - Applies to all other paths
 *
 * Why two chains?
 *   OAuth2 login needs sessions to work properly.
 *   But our API should be stateless (JWT-based).
 *   Mixing them caused issues, so we separate them.
 *
 * @Configuration — tells Spring this class contains configuration beans
 */

package com.connectsphere.auth.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    /** jwtFilter — our custom filter that validates JWT tokens */
    private final JwtFilter jwtFilter;

    /** oAuth2SuccessHandler — runs after Google login succeeds */
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    public SecurityConfig(JwtFilter jwtFilter, OAuth2SuccessHandler oAuth2SuccessHandler) {
        this.jwtFilter = jwtFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
    }

    /**
     * oauth2FilterChain — Security chain for Google OAuth2 routes
     *
     * @Order(1) — this chain is checked FIRST before the API chain
     * .securityMatcher() — this chain ONLY applies to these paths:
     *   /oauth2/**        — Google OAuth2 authorization endpoint
     *   /login/oauth2/**  — Google OAuth2 callback endpoint
     *
     * .csrf(disable)  — disable CSRF protection (not needed for OAuth2 redirect)
     * .cors(disable)  — disable CORS (handled by API Gateway)
     * .permitAll()    — allow all requests to these OAuth2 paths without token
     * .oauth2Login()  — enable OAuth2 login with our custom success handler
     */
    @Bean
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/oauth2/**", "/login/oauth2/**")
            .csrf(c -> c.disable())
            .cors(c -> c.disable())
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .oauth2Login(oauth -> oauth.successHandler(oAuth2SuccessHandler));
        return http.build();
    }

    /**
     * apiFilterChain — Security chain for all regular API routes
     *
     * @Order(2) — this chain is checked SECOND (after OAuth2 chain)
     *
     * .csrf(disable) — CSRF not needed because we use JWT (stateless)
     *   CSRF attacks only work with cookie-based sessions
     *
     * .cors(disable) — CORS is handled by the API Gateway, not here
     *
     * .sessionManagement(STATELESS) — no HTTP sessions created
     *   Every request must include JWT token to prove identity
     *   Server doesn't remember previous requests
     *
     * .authorizeHttpRequests — defines which paths need authentication:
     *   PUBLIC paths (no token needed):
     *     /auth/register      — anyone can register
     *     /auth/login         — anyone can login
     *     /auth/guest         — anyone can get guest token
     *     /auth/forgot-password — anyone can request reset
     *     /auth/reset-password  — anyone with reset token can reset
     *     /auth/user/**       — anyone can view user profiles
     *     /auth/search        — anyone can search users
     *     /error              — Spring Boot error endpoint
     *
     *   PROTECTED paths (token required):
     *     .anyRequest().authenticated() — everything else needs valid JWT
     *     e.g. /auth/profile, /auth/admin/**
     *
     * .exceptionHandling — what to do when unauthenticated request hits protected endpoint
     *   Returns JSON: {"error": "Unauthorized"} with HTTP 401 status
     *   Instead of Spring's default HTML error page
     *
     * .addFilterBefore(jwtFilter, ...) — run our JwtFilter BEFORE Spring's
     *   default UsernamePasswordAuthenticationFilter
     *   This ensures JWT is validated before any other security check
     */
    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(c -> c.disable())
            .cors(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(
                    "/auth/register", "/auth/login", "/auth/guest",
                    "/auth/forgot-password", "/auth/reset-password",
                    "/auth/otp/**",
                    "/auth/user/**", "/auth/search", "/error",
                    "/auth/user/verify-by-email"  // internal: payment-service grants badge
                ).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((request, response, authException) -> {
                    /* Return JSON error instead of HTML page for API clients */
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                }))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * passwordEncoder — BCrypt password hashing bean
     *
     * BCrypt is a one-way hashing algorithm for passwords.
     * "mypassword" → "$2a$10$randomsalt+hash" (cannot be reversed)
     *
     * @Bean — Spring manages this object and injects it wherever needed
     * Used in AuthService to:
     *   - Hash password during registration: passwordEncoder.encode(password)
     *   - Verify password during login: passwordEncoder.matches(input, hash)
     */
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
