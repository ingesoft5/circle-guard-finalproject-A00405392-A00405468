package com.circleguard.gateway.metrics;

import com.circleguard.gateway.service.QrValidationService.ValidationResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Check-ins en la puerta (HU-20). En Prometheus: {@code circleguard_gate_checkins_total{result}}, con
 * {@code result} = {@code granted} (entra), {@code denied_risk} (persona en cerco), {@code denied_invalid}
 * (QR falso o vencido) o {@code denied_unavailable} (no se pudo saber el estado).
 */
@Component
public class GateMetrics {

    static final String CHECKINS = "circleguard.gate.checkins";

    private final MeterRegistry registry;

    static final java.util.List<String> RESULTS = java.util.List.of("granted", "denied_risk", "denied_invalid", "denied_unavailable");

    public GateMetrics(MeterRegistry registry) {
        this.registry = registry;
        // Cada serie existe desde el arranque con valor 0: así Prometheus ve el primer incremento
        // (increase() y rate() no cuentan el salto de una serie que nace con un valor mayor que 0).
        RESULTS.forEach(this::counter);
    }

    public void checkIn(ValidationResult result) {
        counter(resultOf(result)).increment();
    }

    private Counter counter(String result) {
        return Counter.builder(CHECKINS)
                .description("Validaciones de QR en la puerta, por resultado")
                .tag("result", result)
                .register(registry);
    }

    static String resultOf(ValidationResult result) {
        if (result.valid()) {
            return "granted";
        }
        String message = result.message() == null ? "" : result.message();
        if (message.contains("Health Risk")) {
            return "denied_risk";
        }
        if (message.contains("unavailable")) {
            return "denied_unavailable";
        }
        return "denied_invalid";
    }
}
