package org.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    private static final long REFRESH_GRACE_MS = 7L * 24 * 60 * 60 * 1000; // 7 dias após expiração

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration.ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String gerarToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims validarToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValido(String token) {
        try {
            validarToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extrairUsername(String token) {
        return validarToken(token).getSubject();
    }

    public String extrairRole(String token) {
        return validarToken(token).get("role", String.class);
    }

    /**
     * Parseia o token sem verificar expiração, mas dentro de uma janela de graça de 7 dias.
     * Usado exclusivamente pelo endpoint de refresh.
     */
    public Claims validarParaRefresh(String token) {
        try {
            return validarToken(token);
        } catch (ExpiredJwtException e) {
            Claims claims = e.getClaims();
            long expiredAt = claims.getExpiration().getTime();
            if (System.currentTimeMillis() - expiredAt > REFRESH_GRACE_MS) {
                throw new JwtException("Token expirado há mais de 7 dias. Faça login novamente.");
            }
            return claims;
        }
    }
}

