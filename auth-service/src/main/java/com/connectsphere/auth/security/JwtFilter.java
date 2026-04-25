/**
 * JwtFilter.java — JWT Authentication Filter
 *
 * This filter runs on EVERY incoming HTTP request to auth-service.
 * It checks if the request has a valid JWT token.
 * If valid → sets the user as "authenticated" in Spring Security context.
 * If invalid/missing → does nothing (Spring Security will block protected endpoints).
 *
 * Think of it like a security guard at a building:
 *   - Every person entering shows their ID (JWT token)
 *   - Guard checks if ID is valid
 *   - If valid → person is allowed in with their identity confirmed
 *   - If no ID → person can only access public areas
 *
 * OncePerRequestFilter — ensures this filter runs exactly ONCE per request
 * (Spring can sometimes call filters multiple times, this prevents that)
 *
 * @Component — Spring automatically registers this as a filter
 */

package com.connectsphere.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    /** jwtUtil — used to validate token and extract email/role from it */
    private final JwtUtil jwtUtil;

    /**
     * internalToken — special token for service-to-service communication
     * When notification-service calls auth-service to get user info,
     * it uses this token instead of a user JWT token.
     * Read from application.yml: service.internal.token
     * Default value: "internal-service-token" (if not configured)
     */
    @Value("${service.internal.token:internal-service-token}")
    private String internalToken;

    public JwtFilter(JwtUtil jwtUtil) { this.jwtUtil = jwtUtil; }

    /**
     * doFilterInternal() — The main filter logic
     * Runs on every request before it reaches the controller
     *
     * @param request  — the incoming HTTP request
     * @param response — the HTTP response
     * @param chain    — the filter chain (call chain.doFilter to continue processing)
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        /* Read the Authorization header from the request */
        String header = request.getHeader("Authorization");

        /*
         * Check if header exists and starts with "Bearer "
         * Valid format: "Authorization: Bearer eyJhbGci..."
         * If no header or wrong format → skip token processing
         */
        if (header != null && header.startsWith("Bearer ")) {

            /* Extract just the token part (remove "Bearer " prefix) */
            String token = header.substring(7);

            if (token.equals(internalToken)) {
                /*
                 * Internal service-to-service call
                 * Grant ADMIN authority so the service can access protected endpoints
                 * e.g. notification-service calling /auth/admin/users to send global notifications
                 */
                var auth = new UsernamePasswordAuthenticationToken(
                        "internal-service", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(auth);

            } else if (jwtUtil.validateToken(token)) {
                /*
                 * Valid user JWT token
                 * Extract email and role from the token
                 * Create an authentication object and set it in Spring Security context
                 * Now Spring Security knows who this user is for this request
                 */
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);

                /*
                 * UsernamePasswordAuthenticationToken — Spring Security's auth object
                 * Parameters: (principal, credentials, authorities)
                 *   principal   = email (identifies the user)
                 *   credentials = null (we don't need password after token validation)
                 *   authorities = list of roles (e.g. ROLE_USER, ROLE_ADMIN)
                 *
                 * "ROLE_" prefix is required by Spring Security
                 * So "USER" becomes "ROLE_USER", "ADMIN" becomes "ROLE_ADMIN"
                 */
                var auth = new UsernamePasswordAuthenticationToken(
                        email, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));

                /* Set authentication in the security context for this request */
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            /* If token is invalid → do nothing, Spring Security will block protected endpoints */
        }

        /*
         * Continue the filter chain — pass request to the next filter or controller
         * This MUST be called, otherwise the request will be stuck
         */
        chain.doFilter(request, response);
    }
}
