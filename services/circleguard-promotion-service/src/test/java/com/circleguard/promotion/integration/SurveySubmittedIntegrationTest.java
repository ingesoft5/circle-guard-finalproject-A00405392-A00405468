package com.circleguard.promotion.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integración form -> Kafka (survey.submitted) -> promotion, con Kafka, Neo4j y Redis reales.
 *
 * El mensaje se publica con la misma configuración de productor que form-service
 * (clave String + JsonSerializer de Spring) y el mismo contenido que HealthSurveyService.
 * Se verifica lo que dejan los otros servicios: el estado en Neo4j, la clave de Redis que lee
 * gateway y el evento promotion.status.changed que consume notification (StringDeserializer).
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class SurveySubmittedIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @Container
    static Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.26.0-community").withAdminPassword("password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2.5-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /** Productor con la configuración de form-service (application.yml: StringSerializer + JsonSerializer). */
    private KafkaTemplate<String, Object> formServiceProducer() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class)));
    }

    private String statusOf(String anonymousId) {
        return neo4jClient.query("MATCH (u:User {anonymousId: $id}) RETURN u.status AS status")
                .bind(anonymousId).to("id")
                .fetchAs(String.class).one().orElse(null);
    }

    @Test
    void surveyWithSymptomsPromotesUserToSuspectAndNotifiesTheOtherServices() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        neo4jClient.query("CREATE (:User {anonymousId: $id, status: 'ACTIVE'})").bind(anonymousId).to("id").run();

        // Mismo evento que HealthSurveyService.submitSurvey (Map.of con el UUID del usuario)
        formServiceProducer().send("survey.submitted", anonymousId, Map.of(
                "anonymousId", UUID.fromString(anonymousId),
                "hasSymptoms", true,
                "timestamp", System.currentTimeMillis())).get(10, TimeUnit.SECONDS);

        // promotion consume el evento y actualiza el grafo
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(statusOf(anonymousId)).isEqualTo("SUSPECT"));

        // gateway lee este estado para negar el acceso (promotion lo escribe después del grafo)
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(redisTemplate.opsForValue().get("user:status:" + anonymousId)).isEqualTo("SUSPECT"));

        // notification consume promotion.status.changed con StringDeserializer y lee el JSON
        JsonNode statusChanged = readStatusChangedEvent(anonymousId);
        assertThat(statusChanged.path("anonymousId").asText()).isEqualTo(anonymousId);
        assertThat(statusChanged.path("status").asText()).isEqualTo("SUSPECT");
    }

    @Test
    void surveyWithoutSymptomsKeepsUserActive() throws Exception {
        String anonymousId = UUID.randomUUID().toString();
        neo4jClient.query("CREATE (:User {anonymousId: $id, status: 'ACTIVE'})").bind(anonymousId).to("id").run();

        formServiceProducer().send("survey.submitted", anonymousId, Map.of(
                "anonymousId", UUID.fromString(anonymousId),
                "hasSymptoms", false,
                "timestamp", System.currentTimeMillis())).get(10, TimeUnit.SECONDS);

        // Un evento posterior con síntomas de otro usuario sirve de marca: cuando ese se procesa,
        // el primero ya fue consumido (misma partición por defecto en un tópico de una partición).
        String marker = UUID.randomUUID().toString();
        neo4jClient.query("CREATE (:User {anonymousId: $id, status: 'ACTIVE'})").bind(marker).to("id").run();
        formServiceProducer().send("survey.submitted", marker, Map.of(
                "anonymousId", UUID.fromString(marker), "hasSymptoms", true, "timestamp", System.currentTimeMillis()))
                .get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(statusOf(marker)).isEqualTo("SUSPECT"));

        assertThat(statusOf(anonymousId)).isEqualTo("ACTIVE");
    }

    private JsonNode readStatusChangedEvent(String anonymousId) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-notification-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        ObjectMapper mapper = new ObjectMapper();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("promotion.status.changed"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (anonymousId.equals(record.key())) {
                        return mapper.readTree(record.value());
                    }
                }
            }
        }
        throw new AssertionError("promotion no publicó promotion.status.changed para " + anonymousId);
    }
}
