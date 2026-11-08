package com.circleguard.gateway.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración auth -> gateway (+ Redis que escribe promotion), con Redis real y el gateway completo por HTTP.
 *
 * El QR se firma exactamente como QrTokenService de auth (HS256, subject = anonymousId, mismo QR_SECRET)
 * y el estado de salud se escribe en Redis con la misma clave que HealthStatusService de promotion
 * ("user:status:<anonymousId>").
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class QrAccessIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.5-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${qr.secret}")
    private String qrSecret;

    /** Igual que QrTokenService.generateQrToken de auth. */
    private String qrIssuedByAuth(String anonymousId, String secret, long validityMillis) {
        return Jwts.builder()
                .setSubject(anonymousId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + validityMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @SuppressWarnings("rawtypes")
    private Map validate(String token) {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/gate/validate", Map.of("token", token), Map.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        return response.getBody();
    }

    @Test
    void validQrOfAHealthyUserGetsGreen() {
        String anonymousId = UUID.randomUUID().toString();

        Map result = validate(qrIssuedByAuth(anonymousId, qrSecret, 300_000));

        assertThat(result.get("valid")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("GREEN");
    }

    /** Estados que escribe promotion para quien está dentro del cerco (Sospechoso -> Probable -> Confirmado). */
    @ParameterizedTest
    @ValueSource(strings = {"SUSPECT", "PROBABLE", "CONFIRMED"})
    void userFencedByPromotionGetsRed(String status) {
        String anonymousId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + anonymousId, status);

        Map result = validate(qrIssuedByAuth(anonymousId, qrSecret, 300_000));

        assertThat(result.get("valid")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("RED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RECOVERED"})
    void userOutsideTheFenceGetsGreen(String status) {
        String anonymousId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("user:status:" + anonymousId, status);

        assertThat(validate(qrIssuedByAuth(anonymousId, qrSecret, 300_000)).get("status")).isEqualTo("GREEN");
    }

    @Test
    void expiredOrForgedQrIsRejected() {
        String anonymousId = UUID.randomUUID().toString();

        assertThat(validate(qrIssuedByAuth(anonymousId, qrSecret, -1_000)).get("status")).isEqualTo("RED");
        assertThat(validate(qrIssuedByAuth(anonymousId, "otra-clave-que-no-es-la-del-sistema-32c", 300_000))
                .get("status")).isEqualTo("RED");
    }
}
