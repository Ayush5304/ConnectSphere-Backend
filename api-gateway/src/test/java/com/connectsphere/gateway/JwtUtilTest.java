package com.connectsphere.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
    }

    @Test
    void isValid_acceptsSignedTokenAndClaimsExposeSubjectAndRole() {
        String token = token("user@test.com", "ADMIN", new Date(System.currentTimeMillis() + 60_000));

        assertTrue(jwtUtil.isValid(token));
        assertEquals("user@test.com", jwtUtil.getClaims(token).getSubject());
        assertEquals("ADMIN", jwtUtil.getClaims(token).get("role", String.class));
    }

    @Test
    void isValid_rejectsMalformedExpiredAndWrongSignatureTokens() {
        assertFalse(jwtUtil.isValid("not-a-jwt"));
        assertFalse(jwtUtil.isValid(token("old@test.com", "USER", new Date(System.currentTimeMillis() - 1_000))));

        String otherSecret = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String wrongSignature = Jwts.builder()
            .setSubject("user@test.com")
            .signWith(Keys.hmacShaKeyFor(otherSecret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
        assertFalse(jwtUtil.isValid(wrongSignature));
    }

    @Test
    void getClaims_throwsForInvalidToken() {
        assertThrows(RuntimeException.class, () -> jwtUtil.getClaims("bad-token"));
    }

    private String token(String subject, String role, Date expiration) {
        return Jwts.builder()
            .setSubject(subject)
            .claim("role", role)
            .setExpiration(expiration)
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
            .compact();
    }
}
