package com.circleguard.dashboard.client;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-17: prueba con falla inyectada de Retry + Circuit Breaker en dashboard → promotion.
 * promotion-service se reemplaza por un servidor HTTP local que falla a voluntad.
 */
@SpringBootTest(
        classes = PromotionClientResilienceTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"RESILIENCE_RETRY_WAIT=10ms", "RESILIENCE_OPEN_WAIT=500ms"})
class PromotionClientResilienceTest {

    @SpringBootConfiguration
    @ImportAutoConfiguration({AopAutoConfiguration.class, RestTemplateAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
    @Import(PromotionClient.class)
    static class TestApp {
    }

    private static final AtomicInteger hits = new AtomicInteger();
    private static final AtomicReference<String> lastPath = new AtomicReference<>();
    private static volatile IntUnaryOperator behaviour = n -> 200;
    private static final HttpServer promotion = startFakePromotion();

    @DynamicPropertySource
    static void promotionUrl(DynamicPropertyRegistry registry) {
        registry.add("circleguard.promotion-service.url",
                () -> "http://localhost:" + promotion.getAddress().getPort());
    }

    @Autowired
    private PromotionClient client;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void reset() {
        hits.set(0);
        behaviour = n -> 200;
        circuit().reset();
    }

    @AfterAll
    static void stopServer() {
        promotion.stop(0);
    }

    @Test
    void returnsPromotionStatsWhenHealthy() {
        assertThat(client.getHealthStats()).containsEntry("totalActive", 42);
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void transientFailureIsRetried() {
        behaviour = n -> n == 1 ? 502 : 200;

        assertThat(client.getHealthStats()).containsEntry("totalActive", 42);
        assertThat(hits.get()).isEqualTo(2);
    }

    @Test
    void promotionDownDegradesTheDashboardAndOpensTheCircuit() {
        behaviour = n -> 503;

        Map<String, Object> first = client.getHealthStats();
        assertThat(first).containsEntry("degraded", true).containsEntry("error", "Service unavailable");
        assertThat(hits.get()).isEqualTo(3);

        client.getHealthStats();
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int callsWhenOpened = hits.get();

        Map<String, Object> whileOpen = client.getHealthStatsByDepartment("Engineering");
        assertThat(whileOpen).containsEntry("degraded", true).containsEntry("department", "Engineering");
        assertThat(hits.get()).as("con el circuito abierto no se llama a promotion").isEqualTo(callsWhenOpened);
    }

    @Test
    void departmentWithSpacesIsEncodedInThePath() {
        client.getHealthStatsByDepartment("Faculty of Engineering");

        assertThat(lastPath.get()).isEqualTo("/api/v1/health-status/stats/department/Faculty%20of%20Engineering");
    }

    private CircuitBreaker circuit() {
        return circuitBreakers.circuitBreaker(PromotionClient.RESILIENCE_NAME);
    }

    private static HttpServer startFakePromotion() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/api/v1/health-status/stats", exchange -> {
                lastPath.set(exchange.getRequestURI().getRawPath());
                int status = behaviour.applyAsInt(hits.incrementAndGet());
                byte[] body = (status == 200 ? "{\"totalActive\":42}" : "{\"error\":\"inyectado\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
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
