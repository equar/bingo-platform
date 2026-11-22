package com.bakaroo.bingo.backend.feature.auth.service;

import com.bakaroo.bingo.backend.config.JwtProperties;
import com.bakaroo.bingo.backend.feature.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] secretBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT signing secret must contain at least 32 UTF-8 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateToken(User user) {
        return generateToken(user, null);
    }

    public String generateToken(User user, String playerSessionId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenTtlSeconds());

        var builder = Jwts.builder()
                .subject(user.getNormalizedPhone() != null ? user.getNormalizedPhone() : user.getEmail())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("uid", user.getId())
                .claim("tokenVersion", user.getSecurityTokenVersion())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList());
        if (playerSessionId != null) builder.claim("sid", playerSessionId);
        return builder.signWith(secretKey, Jwts.SIG.HS256).compact();
    }

    public String extractPlayerSessionId(String token) {
        return parseClaims(token).get("sid", String.class);
    }

    public long extractTokenVersion(String token) {
        Object value = parseClaims(token).get("tokenVersion");
        if (!(value instanceof Number version)) {
            throw new IllegalArgumentException("Token version claim is missing");
        }
        return version.longValue();
    }

    public Instant extractExpiration(String token) {
        Date expiration = parseClaims(token).getExpiration();
        if (expiration == null) {
            throw new IllegalArgumentException("Token expiration claim is missing");
        }
        return expiration.toInstant();
    }

    public String newPlayerSessionId() { return UUID.randomUUID().toString(); }

    public Instant tokenExpiresAt() {
        return Instant.now().plusSeconds(jwtProperties.accessTokenTtlSeconds());
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object userId = parseClaims(token).get("uid");
        return userId instanceof Number number ? number.longValue() : null;
    }

    public boolean isTokenValid(String token, String subject) {
        Claims claims = parseClaims(token);
        return subject.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
    }

    public boolean isTokenValidForUser(String token, Long userId, long currentTokenVersion) {
        Claims claims = parseClaims(token);
        Object tokenUserId = claims.get("uid");
        Object tokenVersion = claims.get("tokenVersion");
        return userId != null && tokenUserId instanceof Number number && userId.longValue() == number.longValue()
                && tokenVersion instanceof Number version && currentTokenVersion == version.longValue()
                && claims.getExpiration().after(new Date());
    }

    public long getExpirationSeconds() {
        return jwtProperties.accessTokenTtlSeconds();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
