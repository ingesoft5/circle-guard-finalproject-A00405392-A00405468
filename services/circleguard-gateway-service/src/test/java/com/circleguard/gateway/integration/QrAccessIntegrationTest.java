package com.circleguard.gateway.integration;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integración auth -> gateway (+ Redis que escribe promotion), con Redis real y el gateway completo por HTTP.
 *
 * El QR se firma exactamente como QrTokenService de auth (HS256, subject = anonymousId, mismo QR_SECRET)
 * y el estado de salud se escribe en Redis con la misma clave que HealthStatusService de promotion
 * ("user:status:<anonymousId>").
 *
 * HU-19: promotion-service se reemplaza por un servidor HTTP local que responde el estado de Neo4j
 * ({@code /internal/v1/users/{id}/status}), para probar el cache-aside cuando Redis no tiene el estado.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class QrAccessIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.5-alpine").withExposedPorts(6379);

    /** Estado de cada persona en Neo4j, según el promotion falso. */
    private static final Map<String, String> statusInNeo4j = new ConcurrentHashMap<>();
    private static final AtomicInteger promotionCalls = new AtomicInteger();
    private static volatile boolean promotionUp = true;
    private static final HttpServer promotion = startFakePromotion();

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("circleguard.promotion-service.url", () -> "http://localhost:" + promotion.getAddress().getPort());
        registry.add("circleguard.promotion-service.timeout", () -> "500ms");
    }

    @BeforeEach
    void resetPromotion() {
        statusInNeo4j.clear();
        promotionCalls.set(0);
        promotionUp = true;
    }

    @AfterAll
    static void stopPromotion() {
        promotion.stop(0);
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
        statusInNeo4j.put(anonymousId, "ACTIVE");

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

    // ------------------------------------------------------------------ HU-19: cache-aside

    @Test
    void cacheMissAsksPromotionAndStoresTheStatusInRedis() {
        String anonymousId = UUID.randomUUID().toString();
        statusInNeo4j.put(anonymousId, "SUSPECT");
        String token = qrIssuedByAuth(anonymousId, qrSecret, 300_000);

        assertThat(validate(token).get("status")).isEqualTo("RED");
        assertThat(promotionCalls.get()).isEqualTo(1);
        assertThat(redisTemplate.opsForValue().get("user:status:" + anonymousId)).isEqualTo("SUSPECT");
        assertThat(redisTemplate.getExpire("user:status:" + anonymousId)).isPositive();

        assertThat(validate(token).get("status")).isEqualTo("RED");
        assertThat(promotionCalls.get()).as("la segunda validación sale de Redis").isEqualTo(1);
    }

    /** El hueco que corrige HU-19: antes, si Redis perdía los datos, la puerta dejaba pasar a todos. */
    @Test
    void fencedUserIsStillDeniedAfterRedisLosesItsData() {
        String anonymousId = UUID.randomUUID().toString();
        statusInNeo4j.put(anonymousId, "CONFIRMED");
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "CONFIRMED");

        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        Map result = validate(qrIssuedByAuth(anonymousId, qrSecret, 300_000));
        assertThat(result.get("valid")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("RED");
    }

    @Test
    void statusWrittenByPromotionIsNotOverwrittenByTheGateway() {
        String anonymousId = UUID.randomUUID().toString();
        statusInNeo4j.put(anonymousId, "ACTIVE");
        redisTemplate.opsForValue().set("user:status:" + anonymousId, "PROBABLE");

        assertThat(validate(qrIssuedByAuth(anonymousId, qrSecret, 300_000)).get("status")).isEqualTo("RED");
        assertThat(promotionCalls.get()).isZero();
        assertThat(redisTemplate.getExpire("user:status:" + anonymousId)).as("lo de promotion no vence").isEqualTo(-1);
    }

    @Test
    void withoutRedisDataAndWithPromotionDownTheGateFailsClosed() {
        String anonymousId = UUID.randomUUID().toString();
        promotionUp = false;

        Map result = validate(qrIssuedByAuth(anonymousId, qrSecret, 300_000));
        assertThat(result.get("valid")).isEqualTo(false);
        assertThat(result.get("message")).isEqualTo("Access Denied: Health status unavailable, try again");
        assertThat(redisTemplate.hasKey("user:status:" + anonymousId)).isFalse();
    }

    private static HttpServer startFakePromotion() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/internal/v1/users/", exchange -> {
                promotionCalls.incrementAndGet();
                String id = exchange.getRequestURI().getPath().split("/")[4];
                int code = promotionUp ? 200 : 503;
                byte[] body = (promotionUp
                        ? "{\"anonymousId\":\"" + id + "\",\"status\":\"" + statusInNeo4j.getOrDefault(id, "ACTIVE") + "\"}"
                        : "{}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(code, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
