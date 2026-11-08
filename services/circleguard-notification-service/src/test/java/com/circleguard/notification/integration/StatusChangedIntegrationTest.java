package com.circleguard.notification.integration;

import com.circleguard.notification.service.LmsService;
import com.circleguard.notification.service.NotificationDispatcher;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integración promotion -> Kafka (promotion.status.changed) -> notification, con Kafka real.
 *
 * El evento se publica con la configuración de productor de promotion-service (StringSerializer + JsonSerializer)
 * y el mismo contenido que HealthStatusService.updateStatus. notification lo consume con su configuración real
 * (StringDeserializer) y debe despachar el aviso y sincronizar la asistencia remota.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class StatusChangedIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @MockBean
    private NotificationDispatcher dispatcher;

    @MockBean
    private LmsService lmsService;

    private void publishLikePromotion(String anonymousId, String status) throws Exception {
        KafkaTemplate<String, Object> promotionProducer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)));
        Map<String, Object> payload = new HashMap<>();   // HealthStatusService usa un HashMap
        payload.put("anonymousId", anonymousId);
        payload.put("status", status);
        payload.put("timestamp", System.currentTimeMillis());
        promotionProducer.send("promotion.status.changed", anonymousId, payload).get(10, TimeUnit.SECONDS);
    }

    @Test
    void fencedUserIsNotifiedAndRemoteAttendanceIsSynced() throws Exception {
        String anonymousId = UUID.randomUUID().toString();

        publishLikePromotion(anonymousId, "SUSPECT");

        verify(dispatcher, timeout(60_000)).dispatch(anonymousId, "SUSPECT");
        verify(lmsService, timeout(10_000)).syncRemoteAttendance(anonymousId, "SUSPECT");
    }

    @Test
    void returningToActiveDoesNotNotify() throws Exception {
        String healthy = UUID.randomUUID().toString();
        String marker = UUID.randomUUID().toString();

        publishLikePromotion(healthy, "ACTIVE");
        publishLikePromotion(marker, "CONFIRMED");   // marca: llega después en la misma partición

        verify(dispatcher, timeout(60_000)).dispatch(marker, "CONFIRMED");
        verify(dispatcher, never()).dispatch(eq(healthy), anyString());
    }
}
