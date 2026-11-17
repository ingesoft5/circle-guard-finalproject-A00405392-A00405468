package com.circleguard.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Date;
import java.util.Map;

/**
 * Cliente de promotion-service para las estadísticas del dashboard.
 *
 * <p>HU-17: Retry (afuera) y Circuit Breaker (adentro), instancia {@value #RESILIENCE_NAME}.
 * Si promotion no responde, el dashboard no falla: devuelve una respuesta degradada
 * ({@code degraded: true}) y, con el circuito abierto, ni siquiera espera el timeout.
 */
@Component
@Slf4j
public class PromotionClient {

    static final String RESILIENCE_NAME = "promotion";

    private final RestTemplate restTemplate;
    private final String promotionServiceUrl;

    public PromotionClient(
            RestTemplateBuilder builder,
            @Value("${circleguard.promotion-service.url:http://localhost:8088}") String promotionServiceUrl,
            @Value("${circleguard.promotion-service.timeout:2s}") Duration timeout) {
        this.restTemplate = builder.setConnectTimeout(timeout).setReadTimeout(timeout).build();
        this.promotionServiceUrl = promotionServiceUrl;
    }

    @SuppressWarnings("unchecked")
    @Retry(name = RESILIENCE_NAME, fallbackMethod = "statsUnavailable")
    @CircuitBreaker(name = RESILIENCE_NAME)
    public Map<String, Object> getHealthStats() {
        return restTemplate.getForObject(promotionServiceUrl + "/api/v1/health-status/stats", Map.class);
    }

    @SuppressWarnings("unchecked")
    @Retry(name = RESILIENCE_NAME, fallbackMethod = "departmentStatsUnavailable")
    @CircuitBreaker(name = RESILIENCE_NAME)
    public Map<String, Object> getHealthStatsByDepartment(String department) {
        return restTemplate.getForObject(
                promotionServiceUrl + "/api/v1/health-status/stats/department/{department}",
                Map.class, department);
    }

    // Fallbacks: el dashboard es de solo lectura, así que cualquier fallo se degrada a una
    // respuesta vacía marcada; el circuito solo cuenta las caídas (ver record-exceptions).

    private Map<String, Object> statsUnavailable(Throwable cause) {
        log.warn("promotion-service no disponible, estadísticas degradadas: {}", cause.toString());
        return Map.of("error", "Service unavailable", "degraded", true, "timestamp", new Date());
    }

    private Map<String, Object> departmentStatsUnavailable(String department, Throwable cause) {
        log.warn("promotion-service no disponible, estadísticas de {} degradadas: {}", department, cause.toString());
        return Map.of("error", "Service unavailable", "degraded", true,
                "department", department, "timestamp", new Date());
    }
}
