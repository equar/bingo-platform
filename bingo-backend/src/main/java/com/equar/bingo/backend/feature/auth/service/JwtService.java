package com.equar.bingo.backend.feature.auth.service;

import com.equar.bingo.backend.config.JwtProperties;
import com.equar.bingo.backend.feature.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.accessTokenTtlSeconds());

        return Jwts.builder()
                .subject(user.getNormalizedPhone())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("uid", user.getId())
                .claim("roles", user.getRoles().stream().map(Enum::name).toList())
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String subject) {
        Claims claims = parseClaims(token);
        return subject.equals(claims.getSubject()) && claims.getExpiration().after(new Date());
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