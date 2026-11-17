package com.circleguard.auth.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente de identity-service. El login depende de él para obtener el ID anónimo.
 *
 * <p>HU-17: la llamada pasa por Retry (afuera) y Circuit Breaker (adentro). Un fallo pasajero
 * se reintenta; si identity sigue caído, el circuito se abre y el login responde 503 al instante
 * en lugar de dejar al usuario esperando el timeout en cada intento. La configuración está en
 * {@code resilience4j.*} del application.yml (instancia {@value #RESILIENCE_NAME}).
 */
@Component
@Slf4j
public class IdentityClient {

    static final String RESILIENCE_NAME = "identity";

    private final RestTemplate restTemplate;
    private final String identityUrl;

    public IdentityClient(
            RestTemplateBuilder builder,
            @Value("${circleguard.identity-service.url:http://localhost:8083}") String identityServiceUrl,
            @Value("${circleguard.identity-service.timeout:2s}") Duration timeout) {
        // Sin timeout, una llamada colgada bloquea el login para siempre y el circuito nunca se entera.
        this.restTemplate = builder.setConnectTimeout(timeout).setReadTimeout(timeout).build();
        this.identityUrl = identityServiceUrl + "/api/v1/identities/map";
    }

    @Retry(name = RESILIENCE_NAME, fallbackMethod = "identityUnavailable")
    @CircuitBreaker(name = RESILIENCE_NAME)
    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        Map<?, ?> response = restTemplate.postForObject(identityUrl, request, Map.class);
        if (response == null || response.get("anonymousId") == null) {
            throw new IllegalStateException("identity-service respondió sin anonymousId");
        }
        return UUID.fromString(response.get("anonymousId").toString());
    }

    // Fallbacks: solo para identity caído, lento o con error 5xx, y para el circuito abierto.
    // Un 4xx no llega aquí: es un error de la petición y se propaga tal cual.

    private UUID identityUnavailable(String realIdentity, ResourceAccessException e) {
        throw unavailable("sin respuesta (" + e.getMessage() + ")", e);
    }

    private UUID identityUnavailable(String realIdentity, HttpServerErrorException e) {
        throw unavailable("respondió " + e.getStatusCode(), e);
    }

    private UUID identityUnavailable(String realIdentity, CallNotPermittedException e) {
        throw unavailable("circuito abierto, no se llama", e);
    }

    private IdentityServiceUnavailableException unavailable(String reason, Exception cause) {
        log.warn("identity-service no disponible: {}", reason);
        return new IdentityServiceUnavailableException(cause);
    }
}
