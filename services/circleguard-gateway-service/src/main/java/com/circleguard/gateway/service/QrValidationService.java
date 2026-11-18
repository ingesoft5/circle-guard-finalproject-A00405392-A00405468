package com.circleguard.gateway.service;

import com.circleguard.gateway.client.PromotionStatusClient;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Duration;
import java.util.Set;

/**
 * Valida el QR en la puerta y decide si la persona entra según su estado de salud.
 *
 * <p>HU-19, cache-aside: el estado se lee primero de Redis. Si no está (Redis se reinició, perdió los
 * datos o la persona nunca tuvo un cambio de estado), se pide a promotion-service, que lo lee de Neo4j,
 * y se guarda en Redis con TTL para las siguientes validaciones. Antes, una clave ausente significaba
 * "sin riesgo" y la puerta dejaba pasar a todos, incluidas las personas en cerco.
 */
@Service
@Slf4j
public class QrValidationService {

    static final String STATUS_KEY_PREFIX = "user:status:";

    // Estados que escribe promotion-service para quien está dentro del cerco
    // (Sospechoso -> Probable -> Confirmado). CONTAGIED y POTENTIAL son nombres antiguos.
    private static final Set<String> FENCED_STATUSES = Set.of("SUSPECT", "PROBABLE", "CONFIRMED", "CONTAGIED", "POTENTIAL");

    private final StringRedisTemplate redisTemplate;
    private final PromotionStatusClient promotionStatusClient;
    private final String qrSecret;
    private final Duration cacheTtl;

    public QrValidationService(
            StringRedisTemplate redisTemplate,
            PromotionStatusClient promotionStatusClient,
            @Value("${qr.secret}") String qrSecret,
            @Value("${circleguard.status-cache.ttl:5m}") Duration cacheTtl) {
        this.redisTemplate = redisTemplate;
        this.promotionStatusClient = promotionStatusClient;
        this.qrSecret = qrSecret;
        this.cacheTtl = cacheTtl;
    }

    public ValidationResult validateToken(String token) {
        String anonymousId;
        try {
            Key key = Keys.hmacShaKeyFor(qrSecret.getBytes());
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            anonymousId = claims.getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return new ValidationResult(false, "RED", "Invalid or Expired Token");
        }

        String status;
        try {
            status = statusOf(anonymousId);
        } catch (RuntimeException e) {
            // Sin Redis ni promotion no se puede saber si la persona está en cerco: se niega el paso
            // (falla cerrada). Dejarla entrar sería repetir el error que corrige este cambio.
            log.warn("No se pudo obtener el estado de {}: {}", anonymousId, e.toString());
            return new ValidationResult(false, "RED", "Access Denied: Health status unavailable, try again");
        }

        if (FENCED_STATUSES.contains(status)) {
            return new ValidationResult(false, "RED", "Access Denied: Health Risk Detected");
        }
        return new ValidationResult(true, "GREEN", "Welcome to Campus");
    }

    /** Cache-aside: Redis primero; si falta, la fuente de verdad, y se guarda para la próxima. */
    private String statusOf(String anonymousId) {
        String key = STATUS_KEY_PREFIX + anonymousId;
        String cached = readCache(key);
        if (cached != null) {
            return cached;
        }
        String status = promotionStatusClient.fetchStatus(anonymousId);
        writeCache(key, status);
        log.debug("Estado de {} no estaba en Redis; promotion respondió {}", anonymousId, status);
        return status;
    }

    // Redis es una caché: si no responde, se trata como un fallo de caché y se va a la fuente de verdad.

    private String readCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("Redis no disponible al leer {}: {}", key, e.toString());
            return null;
        }
    }

    private void writeCache(String key, String status) {
        try {
            // setIfAbsent: si promotion escribió el estado mientras tanto, gana el suyo (es más nuevo).
            // El TTL acota cuánto puede durar un valor puesto por el gateway (los de promotion no vencen,
            // salvo RECOVERED, que promotion guarda 30 días).
            redisTemplate.opsForValue().setIfAbsent(key, status, cacheTtl);
        } catch (RuntimeException e) {
            log.warn("Redis no disponible al guardar {}: {}", key, e.toString());
        }
    }

    public record ValidationResult(boolean valid, String status, String message) {}
}
