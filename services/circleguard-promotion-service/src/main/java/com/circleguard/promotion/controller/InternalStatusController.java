package com.circleguard.promotion.controller;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Estado de salud de una persona leído de la fuente de verdad (Neo4j), no de Redis.
 *
 * <p>HU-19: lo usa el gateway cuando el estado no está en Redis (cache-aside). Es una ruta interna
 * entre servicios: el nginx del web no la publica ({@code /api/<servicio>/internal/} responde 404).
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalStatusController {

    /** Quien nunca reportó nada no está en el grafo: para el cerco es una persona sin riesgo. */
    static final String DEFAULT_STATUS = "ACTIVE";

    private final UserNodeRepository userNodeRepository;

    @GetMapping("/{anonymousId}/status")
    public Map<String, String> getStatus(@PathVariable String anonymousId) {
        String status = userNodeRepository.findStatusByAnonymousId(anonymousId).orElse(DEFAULT_STATUS);
        return Map.of("anonymousId", anonymousId, "status", status);
    }
}
