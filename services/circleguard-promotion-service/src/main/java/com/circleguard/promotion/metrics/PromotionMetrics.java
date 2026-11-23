package com.circleguard.promotion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Métricas de negocio de promotion (HU-20). En Prometheus:
 * <ul>
 *   <li>{@code circleguard_promotions_total{status}}: personas que pasan a un estado (la persona que reporta y
 *       sus contactos). Con {@code rate(...[5m]) * 60} se obtienen las promociones por minuto.</li>
 *   <li>{@code circleguard_containment_time_seconds}: tiempo de contención, desde que la persona envía el
 *       cuestionario con síntomas (marca de tiempo del evento de form) hasta que promotion dejó aplicado su
 *       cerco (grafo, contactos y Redis). Con histograma para el p95.</li>
 * </ul>
 */
@Component
public class PromotionMetrics {

    static final String PROMOTIONS = "circleguard.promotions";
    static final String CONTAINMENT = "circleguard.containment.time";

    private final MeterRegistry registry;
    private final Timer containment;

    public PromotionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.containment = Timer.builder(CONTAINMENT)
                .description("Desde el cuestionario con síntomas hasta el cerco aplicado")
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(10))
                .maximumExpectedValue(Duration.ofMinutes(5))
                .register(registry);
        // Series en 0 desde el arranque para que Prometheus cuente el primer incremento.
        KNOWN_STATUSES.forEach(status -> counter(status));
    }

    static final java.util.List<String> KNOWN_STATUSES = java.util.List.of("SUSPECT", "PROBABLE", "CONFIRMED", "RECOVERED", "ACTIVE");

    private Counter counter(String status) {
        return Counter.builder(PROMOTIONS)
                .description("Personas que pasan a un estado de salud")
                .tag("status", status)
                .register(registry);
    }

    /** Suma {@code people} personas que pasaron al estado {@code status}. */
    public void promoted(String status, int people) {
        if (people <= 0 || status == null) {
            return;
        }
        counter(status).increment(people);
    }

    /** Registra el tiempo de contención; se ignora si la marca de tiempo del evento no es válida. */
    public void containedSince(long reportedAtMillis) {
        long elapsed = System.currentTimeMillis() - reportedAtMillis;
        if (reportedAtMillis > 0 && elapsed >= 0) {
            containment.record(Duration.ofMillis(elapsed));
        }
    }
}
