package com.connectsphere.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private JwtAuthFilter filterFactory;

    @BeforeEach
    void setUp() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        filterFactory = new JwtAuthFilter(jwtUtil);
        ReflectionTestUtils.setField(filterFactory, "internalToken", "internal-service-token");
    }

    @Test
    void optionsRequestBypassesAuth() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.options("/api/private").build());
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        run(exchange, chain(called));

        assertTrue(called.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void publicGetAndExactAuthRoutesBypassAuth() {
        MockServerWebExchange authExchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").build());
        AtomicReference<Boolean> authCalled = new AtomicReference<>(false);
        run(authExchange, chain(authCalled));
        assertTrue(authCalled.get());

        MockServerWebExchange feedExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/posts").build());
        AtomicReference<Boolean> feedCalled = new AtomicReference<>(false);
        run(feedExchange, chain(feedCalled));
        assertTrue(feedCalled.get());
    }

    @Test
    void privateRouteWithoutBearerTokenReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/posts").build());
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        run(exchange, chain(called));

        assertFalse(called.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void internalTokenBypassesJwtValidation() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/posts")
            .header(HttpHeaders.AUTHORIZATION, "Bearer internal-service-token").build());
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        run(exchange, chain(called));

        assertTrue(called.get());
    }

    @Test
    void validJwtAddsUserHeadersToDownstreamRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/posts")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("member@test.com", "USER")).build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, e -> {
            seen.set(e);
            return Mono.empty();
        });

        assertEquals("member@test.com", seen.get().getRequest().getHeaders().getFirst("X-User-Email"));
        assertEquals("USER", seen.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    @Test
    void invalidJwtReturnsUnauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/posts")
            .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token").build());

        run(exchange, chain(new AtomicReference<>(false)));

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void validJwtWithoutRoleDefaultsToUser() {
        String token = Jwts.builder()
            .setSubject("norole@test.com")
            .setExpiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/private")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();

        run(exchange, e -> {
            seen.set(e);
            return Mono.empty();
        });

        assertEquals("USER", seen.get().getRequest().getHeaders().getFirst("X-User-Role"));
    }

    private GatewayFilterChain chain(AtomicReference<Boolean> called) {
        return exchange -> {
            called.set(true);
            return Mono.empty();
        };
    }

    private void run(MockServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayFilter filter = filterFactory.apply(new JwtAuthFilter.Config());
        filter.filter(exchange, chain).block();
    }

    private String token(String subject, String role) {
        return Jwts.builder()
            .setSubject(subject)
            .claim("role", role)
            .setExpiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
    }
}
