package com.circleguard.promotion.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-20: métricas de negocio de promotion. */
class PromotionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final PromotionMetrics metrics = new PromotionMetrics(registry);

    @Test
    void promotionsAreCountedPerStatus() {
        metrics.promoted("CONFIRMED", 1);
        metrics.promoted("SUSPECT", 3);
        metrics.promoted("SUSPECT", 2);
        metrics.promoted("PROBABLE", 0);   // nadie cambió: no suma

        assertThat(registry.counter("circleguard.promotions", "status", "CONFIRMED").count()).isEqualTo(1.0);
        assertThat(registry.counter("circleguard.promotions", "status", "SUSPECT").count()).isEqualTo(5.0);
        assertThat(registry.counter("circleguard.promotions", "status", "PROBABLE").count()).isZero();
    }

    @Test
    void knownStatusesStartAtZeroSoPrometheusSeesTheFirstIncrement() {
        assertThat(registry.find("circleguard.promotions").counters()).hasSize(PromotionMetrics.KNOWN_STATUSES.size());
        assertThat(registry.find("circleguard.promotions").counters()).allMatch(c -> c.count() == 0.0);
    }

    @Test
    void containmentTimeIsMeasuredFromTheReportTimestamp() {
        metrics.containedSince(System.currentTimeMillis() - 2_000);

        var timer = registry.timer("circleguard.containment.time");
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isBetween(2_000.0, 10_000.0);
    }

    @Test
    void invalidTimestampsAreIgnored() {
        metrics.containedSince(0);
        metrics.containedSince(System.currentTimeMillis() + 60_000);   // reloj adelantado

        assertThat(registry.timer("circleguard.containment.time").count()).isZero();
    }
}
