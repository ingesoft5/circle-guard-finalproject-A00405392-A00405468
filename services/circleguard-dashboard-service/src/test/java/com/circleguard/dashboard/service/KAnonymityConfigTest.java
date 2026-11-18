package com.circleguard.dashboard.service;

import com.circleguard.dashboard.client.PromotionClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HU-18 (#25): el K del k-anonimato es configuración externa ({@code circleguard.privacy.k-anonymity}).
 * Antes estaba fijo en el código y, además, duplicado: el filtro usaba 5 y {@code AnalyticsService} tenía su propio {@code < 5}.
 */
class KAnonymityConfigTest {

    private static Map<String, Object> stats(long totalUsers, long greenCount) {
        Map<String, Object> m = new HashMap<>();
        m.put("totalUsers", totalUsers);
        m.put("greenCount", greenCount);
        return m;
    }

    @Test
    void conKDeCincoUnaPoblacionDeTresSeEnmascara() {
        Map<String, Object> out = new KAnonymityFilter(5).apply(stats(3, 3));

        assertThat(out).containsEntry("totalUsers", "<5").containsEntry("note", "Insufficient data for privacy");
    }

    @Test
    void conKDeTresLaMismaPoblacionYaNoSeEnmascara() {
        Map<String, Object> out = new KAnonymityFilter(3).apply(stats(3, 3));

        assertThat(out).containsEntry("totalUsers", 3L).containsEntry("greenCount", 3L).doesNotContainKey("note");
    }

    @Test
    void conKDeDiezUnConteoDeSieteSeEnmascaraConElValorConfigurado() {
        Map<String, Object> out = new KAnonymityFilter(10).apply(stats(200, 7));

        assertThat(out).containsEntry("greenCount", "<10");
    }

    @Test
    void unKInvalidoHaceFallarElArranqueEnLugarDeDesactivarLaProteccion() {
        assertThatThrownBy(() -> new KAnonymityFilter(0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("k-anonymity");
        assertThatThrownBy(() -> new KAnonymityFilter(-2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elValorSeLeeDeLaConfiguracionExterna() {
        new ApplicationContextRunner().withUserConfiguration(KAnonymityFilter.class)
                .withPropertyValues("circleguard.privacy.k-anonymity=8")
                .run(ctx -> assertThat(ctx.getBean(KAnonymityFilter.class).k()).isEqualTo(8));
    }

    @Test
    void sinConfiguracionElKPorDefectoEsCinco() {
        new ApplicationContextRunner().withUserConfiguration(KAnonymityFilter.class)
                .run(ctx -> assertThat(ctx.getBean(KAnonymityFilter.class).k()).isEqualTo(5));
    }

    @Test
    void unKInvalidoEnLaConfiguracionImpideArrancar() {
        new ApplicationContextRunner().withUserConfiguration(KAnonymityFilter.class)
                .withPropertyValues("circleguard.privacy.k-anonymity=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    // --- Las tendencias de entrada usan el MISMO K que el filtro (antes tenían un 5 propio) -------------

    private AnalyticsService serviceWith(int k, long... counts) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (long c : counts) {
            rows.add(new HashMap<>(Map.of("entry_count", c)));
        }
        when(jdbc.queryForList(any(String.class), eq(UUID.fromString("00000000-0000-0000-0000-000000000001")))).thenReturn(rows);
        return new AnalyticsService(jdbc, mock(PromotionClient.class), new KAnonymityFilter(k));
    }

    private static final UUID LOCATION = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void lasTendenciasEnmascaranConElKConfigurado() {
        List<Map<String, Object>> rows = serviceWith(5, 3, 4, 10).getEntryTrends(LOCATION);

        assertThat(rows.get(0)).containsEntry("entry_count", "<5").containsEntry("note", "Insufficient data for privacy");
        assertThat(rows.get(1)).containsEntry("entry_count", "<5");
        assertThat(rows.get(2)).containsEntry("entry_count", 10L).doesNotContainKey("note");
    }

    @Test
    void conUnKMasBajoLasTendenciasYaNoEnmascaranEsosConteos() {
        List<Map<String, Object>> rows = serviceWith(3, 3, 4, 2).getEntryTrends(LOCATION);

        assertThat(rows.get(0)).containsEntry("entry_count", 3L);
        assertThat(rows.get(1)).containsEntry("entry_count", 4L);
        assertThat(rows.get(2)).containsEntry("entry_count", "<3");
    }
}
