package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServicesTest {

    private static final String JWT_SECRET = "unit-test-jwt-secret-key-with-32-chars-min";
    private static final String QR_SECRET = "unit-test-qr-secret-key-with-32-chars-mini";

    private Claims parse(String secret, String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    @Test
    void jwtContainsAnonymousIdAndPermissions() {
        JwtTokenService service = new JwtTokenService(JWT_SECRET, 60_000);
        UUID anonymousId = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken("health_user", null,
                List.of(new SimpleGrantedAuthority("ROLE_HEALTH_CENTER"), new SimpleGrantedAuthority("identity:lookup")));

        Claims claims = parse(JWT_SECRET, service.generateToken(anonymousId, auth));

        assertThat(claims.getSubject()).isEqualTo(anonymousId.toString());
        assertThat(claims.get("permissions", List.class)).containsExactlyInAnyOrder("ROLE_HEALTH_CENTER", "identity:lookup");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void jwtSignedWithAnotherKeyIsRejected() {
        JwtTokenService service = new JwtTokenService(JWT_SECRET, 60_000);
        String token = service.generateToken(UUID.randomUUID(), new UsernamePasswordAuthenticationToken("u", null, List.of()));

        assertThatThrownBy(() -> parse(QR_SECRET, token)).isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    void qrTokenIsValidForTheConfiguredMilliseconds() {
        QrTokenService service = new QrTokenService(QR_SECRET, 300_000);
        UUID anonymousId = UUID.randomUUID();

        Claims claims = parse(QR_SECRET, service.generateQrToken(anonymousId));

        assertThat(claims.getSubject()).isEqualTo(anonymousId.toString());
        long validitySeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(validitySeconds).isBetween(299L, 300L);
    }

    @Test
    void expiredQrTokenIsRejected() {
        QrTokenService service = new QrTokenService(QR_SECRET, -1_000);
        String token = service.generateQrToken(UUID.randomUUID());

        assertThatThrownBy(() -> parse(QR_SECRET, token)).isInstanceOf(ExpiredJwtException.class);
    }
}
