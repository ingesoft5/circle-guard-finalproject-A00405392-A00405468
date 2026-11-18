package com.circleguard.gateway.service;

import com.circleguard.gateway.client.PromotionStatusClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.ResourceAccessException;

import java.security.Key;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class QrValidationServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private QrValidationService service;
    private ValueOperations<String, String> valueOps;
    private PromotionStatusClient promotion;
    private final String secret = "my-super-secret-test-key-32-chars-long";
    private final String anonymousId = UUID.randomUUID().toString();
    private final String key = "user:status:" + anonymousId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        promotion = Mockito.mock(PromotionStatusClient.class);

        service = new QrValidationService(redisTemplate, promotion, secret, TTL);
    }

    private String token() {
        Key k = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder().setSubject(anonymousId).signWith(k, SignatureAlgorithm.HS256).compact();
    }

    @Test
    void shouldValidateCorrectTokenAndAllowAccess() {
        Mockito.when(valueOps.get(key)).thenReturn("CLEAR");

        QrValidationService.ValidationResult result = service.validateToken(token());

        assertTrue(result.valid());
        assertEquals("GREEN", result.status());
        Mockito.verifyNoInteractions(promotion);
    }

    @Test
    void shouldDenyAccessForContagiedUser() {
        Mockito.when(valueOps.get(key)).thenReturn("CONTAGIED");

        QrValidationService.ValidationResult result = service.validateToken(token());

        assertFalse(result.valid());
        assertEquals("RED", result.status());
    }

    @Test
    void cacheMissAsksPromotionAndStoresTheStatusWithTtl() {
        Mockito.when(valueOps.get(key)).thenReturn(null);
        Mockito.when(promotion.fetchStatus(anonymousId)).thenReturn("SUSPECT");

        QrValidationService.ValidationResult result = service.validateToken(token());

        assertFalse(result.valid());
        assertEquals("Access Denied: Health Risk Detected", result.message());
        Mockito.verify(valueOps).setIfAbsent(key, "SUSPECT", TTL);
    }

    @Test
    void cacheMissOfAHealthyUserLetsThemIn() {
        Mockito.when(valueOps.get(key)).thenReturn(null);
        Mockito.when(promotion.fetchStatus(anonymousId)).thenReturn("ACTIVE");

        assertTrue(service.validateToken(token()).valid());
        Mockito.verify(valueOps).setIfAbsent(key, "ACTIVE", TTL);
    }

    @Test
    void cacheMissWithPromotionDownFailsClosed() {
        Mockito.when(valueOps.get(key)).thenReturn(null);
        Mockito.when(promotion.fetchStatus(anonymousId)).thenThrow(new ResourceAccessException("caido"));

        QrValidationService.ValidationResult result = service.validateToken(token());

        assertFalse(result.valid());
        assertEquals("Access Denied: Health status unavailable, try again", result.message());
        Mockito.verify(valueOps, Mockito.never()).setIfAbsent(Mockito.any(), Mockito.any(), Mockito.any(Duration.class));
    }

    @Test
    void redisDownFallsBackToPromotion() {
        Mockito.when(valueOps.get(key)).thenThrow(new RedisConnectionFailureException("caido"));
        Mockito.when(valueOps.setIfAbsent(key, "PROBABLE", TTL)).thenThrow(new RedisConnectionFailureException("caido"));
        Mockito.when(promotion.fetchStatus(anonymousId)).thenReturn("PROBABLE");

        QrValidationService.ValidationResult result = service.validateToken(token());

        assertFalse(result.valid());
        assertEquals("Access Denied: Health Risk Detected", result.message());
    }

    @Test
    void forgedTokenIsRejectedWithoutLookingUpTheStatus() {
        Key other = Keys.hmacShaKeyFor("otra-clave-que-no-es-la-del-sistema-32c".getBytes());
        String forged = Jwts.builder().setSubject(anonymousId).signWith(other, SignatureAlgorithm.HS256).compact();

        QrValidationService.ValidationResult result = service.validateToken(forged);

        assertEquals("Invalid or Expired Token", result.message());
        Mockito.verifyNoInteractions(valueOps, promotion);
    }
}
