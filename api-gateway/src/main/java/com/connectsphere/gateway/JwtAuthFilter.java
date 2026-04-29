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

    // ✅ PUBLIC APIs (no JWT required)
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/guest",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/user",
            "/api/auth/search",
            "/oauth2/",
            "/login/oauth2/",
            "/posts/",
            "/comments/",
            "/likes/",
            "/follows/",
            "/search",
            "/hashtags/",
            "/media/files/",
            "/actuator"
    );

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            String path = exchange.getRequest().getURI().getPath();

            // ✅ 1. Allow CORS preflight (VERY IMPORTANT)
            if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
                return chain.filter(exchange);
            }

            // ✅ DEBUG (optional)
            System.out.println("Incoming Path: " + path);

            // ✅ 2. Allow public APIs
            if (isPublic(path)) {
                return chain.filter(exchange);
            }

            // ✅ 3. Get Authorization header
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // ❌ 4. No token → Unauthorized
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange);
            }

            // ✅ 5. Extract token
            String token = authHeader.substring(7);

            // ✅ 6. Internal service bypass
            if (token.equals(internalToken)) {
                return chain.filter(exchange);
            }

            // ❌ 7. Invalid token
            if (!jwtUtil.isValid(token)) {
                return unauthorized(exchange);
            }

            try {
                // ✅ 8. Extract user info
                Claims claims = jwtUtil.getClaims(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                // ✅ 9. Add headers for downstream services
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

    // ✅ FIXED PUBLIC PATH CHECK
    private boolean isPublic(String path) {
        return PUBLIC_PATHS.stream().anyMatch(p -> path.contains(p));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
    }
}