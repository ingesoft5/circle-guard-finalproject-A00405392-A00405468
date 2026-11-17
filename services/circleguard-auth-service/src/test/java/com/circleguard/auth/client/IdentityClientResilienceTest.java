package com.circleguard.auth.client;

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
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-17: prueba con falla inyectada de Retry + Circuit Breaker en auth → identity.
 *
 * <p>identity-service se reemplaza por un servidor HTTP local al que se le inyectan fallas
 * (5xx, lentitud, recuperación) y que cuenta cuántas llamadas le llegan. Se carga solo el
 * cliente con la configuración real de {@code resilience4j.yml}; aquí solo
 * se acortan las esperas para que la prueba sea rápida.
 */
@SpringBootTest(
        classes = IdentityClientResilienceTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // src/test/resources/application.yml de auth tapa al principal en las pruebas,
                // así que se importa explícitamente el mismo archivo que usa producción.
                "spring.config.import=classpath:resilience4j.yml",
                "RESILIENCE_RETRY_WAIT=10ms",
                "RESILIENCE_OPEN_WAIT=500ms",
                "circleguard.identity-service.timeout=300ms"
        })
class IdentityClientResilienceTest {

    @SpringBootConfiguration
    @ImportAutoConfiguration({AopAutoConfiguration.class, RestTemplateAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class})
    @Import(IdentityClient.class)
    static class TestApp {
    }

    private static final UUID ANON_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final AtomicInteger hits = new AtomicInteger();
    /** Recibe el número de llamada (1, 2, ...) y devuelve el código HTTP; 0 = responder lento. */
    private static volatile IntUnaryOperator behaviour = n -> 200;
    private static final HttpServer identity = startFakeIdentity();

    @DynamicPropertySource
    static void identityUrl(DynamicPropertyRegistry registry) {
        registry.add("circleguard.identity-service.url",
                () -> "http://localhost:" + identity.getAddress().getPort());
    }

    @Autowired
    private IdentityClient client;

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
        identity.stop(0);
    }

    @Test
    void transientFailureIsRetriedAndTheLoginSucceeds() {
        behaviour = n -> n <= 2 ? 503 : 200;          // falla 2 veces y se recupera

        assertThat(client.getAnonymousId("health_user")).isEqualTo(ANON_ID);
        assertThat(hits.get()).isEqualTo(3);          // 1 intento + 2 reintentos
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void identityDownOpensTheCircuitAndThenFailsFastWithoutCallingIt() {
        behaviour = n -> 500;                         // identity caído

        assertThatThrownBy(() -> client.getAnonymousId("health_user"))
                .isInstanceOf(IdentityServiceUnavailableException.class);
        assertThat(hits.get()).isEqualTo(3);
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.CLOSED); // 3 llamadas < mínimo de 5

        assertThatThrownBy(() -> client.getAnonymousId("health_user"))
                .isInstanceOf(IdentityServiceUnavailableException.class);
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.OPEN);   // 5 de 5 fallaron
        int callsWhenOpened = hits.get();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> client.getAnonymousId("health_user"))
                    .isInstanceOf(IdentityServiceUnavailableException.class);
        }
        assertThat(hits.get()).as("con el circuito abierto no se llama a identity")
                .isEqualTo(callsWhenOpened);
    }

    @Test
    void circuitClosesAgainWhenIdentityRecovers() throws InterruptedException {
        behaviour = n -> 500;
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> client.getAnonymousId("health_user"))
                    .isInstanceOf(IdentityServiceUnavailableException.class);
        }
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        behaviour = n -> 200;                         // identity vuelve
        Thread.sleep(700);                            // pasa el tiempo de espera en abierto (500 ms en la prueba)
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        assertThat(client.getAnonymousId("health_user")).isEqualTo(ANON_ID);
        assertThat(client.getAnonymousId("health_user")).isEqualTo(ANON_ID);
        assertThat(circuit().getState()).isEqualTo(CircuitBreaker.State.CLOSED); // 2 de prueba bien
    }

    @Test
    void slowIdentityCountsAsAFailureThanksToTheTimeout() {
        behaviour = n -> 0;                           // responde después del timeout (300 ms en la prueba)

        assertThatThrownBy(() -> client.getAnonymousId("health_user"))
                .isInstanceOf(IdentityServiceUnavailableException.class);
        assertThat(hits.get()).isEqualTo(3);
        assertThat(circuit().getMetrics().getNumberOfFailedCalls()).isEqualTo(3);
    }

    @Test
    void clientErrorsAreNeitherRetriedNorCountedAsFailures() {
        behaviour = n -> 400;                         // la petición está mal: reintentar no sirve

        assertThatThrownBy(() -> client.getAnonymousId("health_user"))
                .isInstanceOf(HttpClientErrorException.class);
        assertThat(hits.get()).isEqualTo(1);
        assertThat(circuit().getMetrics().getNumberOfFailedCalls()).isZero();
    }

    private CircuitBreaker circuit() {
        return circuitBreakers.circuitBreaker(IdentityClient.RESILIENCE_NAME);
    }

    private static HttpServer startFakeIdentity() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/api/v1/identities/map", exchange -> {
                int status = behaviour.applyAsInt(hits.incrementAndGet());
                if (status == 0) {
                    sleep(1_000);
                    status = 200;
                }
                byte[] body = (status == 200
                        ? "{\"anonymousId\":\"" + ANON_ID + "\"}"
                        : "{\"error\":\"inyectado\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                try {
                    exchange.sendResponseHeaders(status, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                } catch (IOException clientGaveUp) {
                    // El cliente ya cortó por timeout: es justo lo que se quiere probar.
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
