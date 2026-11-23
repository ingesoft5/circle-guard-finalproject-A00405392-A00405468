package com.circleguard.gateway.metrics;

import com.circleguard.gateway.service.QrValidationService.ValidationResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-20: check-ins de la puerta por resultado. */
class GateMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final GateMetrics metrics = new GateMetrics(registry);

    private double count(String result) {
        return registry.counter("circleguard.gate.checkins", "result", result).count();
    }

    @Test
    void everyResultStartsAtZero() {
        assertThat(GateMetrics.RESULTS).allMatch(r -> count(r) == 0.0);
        assertThat(registry.find("circleguard.gate.checkins").counters()).hasSize(4);
    }

    @Test
    void checkInsAreCountedByResult() {
        metrics.checkIn(new ValidationResult(true, "GREEN", "Welcome to Campus"));
        metrics.checkIn(new ValidationResult(true, "GREEN", "Welcome to Campus"));
        metrics.checkIn(new ValidationResult(false, "RED", "Access Denied: Health Risk Detected"));
        metrics.checkIn(new ValidationResult(false, "RED", "Invalid or Expired Token"));
        metrics.checkIn(new ValidationResult(false, "RED", "Access Denied: Health status unavailable, try again"));

        assertThat(count("granted")).isEqualTo(2.0);
        assertThat(count("denied_risk")).isEqualTo(1.0);
        assertThat(count("denied_invalid")).isEqualTo(1.0);
        assertThat(count("denied_unavailable")).isEqualTo(1.0);
    }
}
