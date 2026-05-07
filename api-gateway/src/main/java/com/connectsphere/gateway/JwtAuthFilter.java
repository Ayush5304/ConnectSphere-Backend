package com.connectsphere.gateway;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtUtil jwtUtil;

    @Value("${service.internal.token:internal-service-token}")
    private String internalToken;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    // Public APIs. Mutating and admin endpoints stay protected even when their
    // path starts with a public read prefix.
    private static final List<String> PUBLIC_EXACT = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/guest",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/otp/login/request",
            "/api/auth/otp/login/verify",
            "/api/auth/otp/register/request",
            "/api/auth/otp/register/verify",
            "/auth/register",
            "/auth/login",
            "/auth/guest",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/otp/login/request",
            "/auth/otp/login/verify",
            "/auth/otp/register/request",
            "/auth/otp/register/verify"
    );

    private static final List<String> PUBLIC_ANY_METHOD_PREFIXES = List.of(
            "/media/files/",
            "/oauth2/",
            "/login/oauth2/",
            "/actuator"
    );

    private static final List<String> PUBLIC_GET_PREFIXES = List.of(
            "/api/auth/user/",
            "/api/auth/search",
            "/api/posts",
            "/api/comments",
            "/api/likes",
            "/api/follows",
            "/api/search",
            "/api/hashtags",
            "/auth/user/",
            "/auth/search",
            "/posts",
            "/comments",
            "/likes",
            "/follows",
            "/search",
            "/hashtags"
    );

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            String path = exchange.getRequest().getURI().getPath();
            HttpMethod method = exchange.getRequest().getMethod();

            // âœ… 1. Allow CORS preflight (VERY IMPORTANT)
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }

            // âœ… DEBUG (optional)
            System.out.println("Incoming Path: " + path);

            // âœ… 2. Allow public APIs
            if (isPublic(path, method)) {
                return chain.filter(exchange);
            }

            // âœ… 3. Get Authorization header
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // âŒ 4. No token â†’ Unauthorized
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange);
            }

            // âœ… 5. Extract token
            String token = authHeader.substring(7);

            // âœ… 6. Internal service bypass
            if (token.equals(internalToken)) {
                return chain.filter(exchange);
            }

            // âŒ 7. Invalid token
            if (!jwtUtil.isValid(token)) {
                return unauthorized(exchange);
            }

            try {
                // âœ… 8. Extract user info
                Claims claims = jwtUtil.getClaims(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                // âœ… 9. Add headers for downstream services
                ServerWebExchange mutated = exchange.mutate()
                        .request(r -> r.header("X-User-Email", email)
                                .header("X-User-Role", role != null ? role : "USER"))
                        .build();

                return chain.filter(mutated);

            } catch (Exception e) {
                return unauthorized(exchange);
            }
        };
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (PUBLIC_EXACT.contains(path)) return true;
        if (PUBLIC_ANY_METHOD_PREFIXES.stream().anyMatch(path::startsWith)) return true;
        if (HttpMethod.GET.equals(method)
                && path.startsWith("/api/notifications/user/")
                && path.endsWith("/stream")) {
            return true;
        }
        return HttpMethod.GET.equals(method)
                && PUBLIC_GET_PREFIXES.stream().anyMatch(path::startsWith)
                && !path.contains("/admin/");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
    }
}

