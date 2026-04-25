/**
 * JwtUtil.java — JWT Token Generator and Validator
 *
 * JWT = JSON Web Token
 * A token is like a digital ID card given to users after login.
 * Every API request includes this token to prove who the user is.
 *
 * Token structure (3 parts separated by dots):
 *   Header.Payload.Signature
 *   e.g. eyJhbGci....eyJzdWIi....abc123
 *
 * Payload contains:
 *   - sub (subject) = user's email
 *   - role = USER / ADMIN / GUEST
 *   - iat = issued at (when token was created)
 *   - exp = expiration (when token expires — 24 hours)
 *
 * Why JWT?
 *   - Stateless — server doesn't store sessions
 *   - Any service can verify the token using the same secret key
 *   - Fast — no database lookup needed to verify identity
 *
 * @Component — Spring creates one instance of this class and manages it
 */

package com.connectsphere.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    /**
     * secret — The secret key used to sign and verify tokens
     * Read from application.yml: jwt.secret
     * Must be at least 256 bits (32 characters) for HS256 algorithm
     * NEVER share this key — anyone with it can create fake tokens
     *
     * @Value — Spring automatically injects the value from application.yml
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * expiration — How long the token is valid (in milliseconds)
     * Read from application.yml: jwt.expiration = 86400000 (24 hours)
     * After expiry, user must login again to get a new token
     */
    @Value("${jwt.expiration}")
    private long expiration;

    /**
     * key() — Creates a cryptographic key from the secret string
     * Keys.hmacShaKeyFor() converts the secret string to a Key object
     * Used for both signing (creating) and verifying tokens
     */
    private Key key() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * generateToken() — Creates a new JWT token after successful login
     *
     * @param email — stored as the "subject" of the token
     * @param role  — stored as a custom claim "role"
     * @return      — the complete JWT token string
     *
     * .setSubject(email)    — who this token belongs to
     * .claim("role", role)  — adds role as extra data in the token
     * .setIssuedAt(now)     — when the token was created
     * .setExpiration(...)   — when the token expires (now + 24 hours)
     * .signWith(key, HS256) — signs with our secret key using HS256 algorithm
     * .compact()            — builds the final token string
     */
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * extractEmail() — Reads the email from inside the token
     * .getBody().getSubject() — gets the "sub" field from the payload
     * Used by JwtFilter to identify which user made the request
     */
    public String extractEmail(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    /**
     * extractRole() — Reads the role from inside the token
     * .getBody().get("role") — gets the custom "role" claim
     * Returns "USER" as default if role is missing or token is invalid
     */
    public String extractRole(String token) {
        try {
            Object role = Jwts.parserBuilder().setSigningKey(key()).build()
                    .parseClaimsJws(token).getBody().get("role");
            return role != null ? role.toString() : "USER";
        } catch (Exception e) { return "USER"; }
    }

    /**
     * validateToken() — Checks if a token is valid
     *
     * Returns true if:
     *   - Token signature is correct (not tampered with)
     *   - Token has not expired
     *
     * Returns false if:
     *   - Token is expired
     *   - Token signature is wrong (someone modified it)
     *   - Token is malformed
     *
     * JwtException is thrown by the library for any invalid token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
