package whatsapp.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
public class TokenService {
    private final SecretKey key;
    private final long expirationTimeMs;

    public TokenService(String secretKey, long expirationTimeMs){
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("JWT secret key environment property 'jwt.secret' cannot be null or empty.");
        }

        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 256 bits (32 bytes) long for HMAC-SHA algorithms.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationTimeMs = expirationTimeMs;
    }

    public String getToken(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Cannot generate JWT token for a blank username.");
        }

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTimeMs);

        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = extractAllClaims(token);
            return claims != null ? claims.getSubject() : null;
        } catch (JwtException e) {
            log.warn("Failed to extract username from JWT token: {}", e.getMessage());
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Claims claims = extractAllClaims(token);
            return claims != null && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token structure or signature: {}", e.getMessage());
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
