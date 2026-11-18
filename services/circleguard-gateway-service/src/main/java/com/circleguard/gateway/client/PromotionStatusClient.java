package com.circleguard.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Pide a promotion-service el estado de salud de una persona (fuente de verdad: Neo4j).
 * Solo se usa cuando el estado no está en Redis (HU-19, cache-aside).
 */
@Component
public class PromotionStatusClient {

    private final RestTemplate restTemplate;
    private final String statusUrl;

    public PromotionStatusClient(
            RestTemplateBuilder builder,
            @Value("${circleguard.promotion-service.url}") String promotionUrl,
            @Value("${circleguard.promotion-service.timeout:2s}") Duration timeout) {
        // En la puerta no se puede esperar: si promotion tarda, se corta y la validación decide.
        this.restTemplate = builder.setConnectTimeout(timeout).setReadTimeout(timeout).build();
        this.statusUrl = promotionUrl + "/internal/v1/users/{anonymousId}/status";
    }

    /** @return el estado, o una excepción si promotion no responde o no trae estado. */
    public String fetchStatus(String anonymousId) {
        Map<?, ?> body = restTemplate.getForObject(statusUrl, Map.class, anonymousId);
        if (body == null || body.get("status") == null) {
            throw new IllegalStateException("promotion-service respondió sin estado");
        }
        return body.get("status").toString();
    }
}
