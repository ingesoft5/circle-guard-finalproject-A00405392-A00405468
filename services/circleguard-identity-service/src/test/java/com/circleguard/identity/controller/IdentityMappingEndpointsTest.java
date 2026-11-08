package com.circleguard.identity.controller;

import com.circleguard.identity.config.SecurityConfig;
import com.circleguard.identity.service.IdentityVaultService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints públicos de mapeo y la autenticación con un JWT real (filtro + reglas de SecurityConfig). */
@WebMvcTest(IdentityVaultController.class)
@Import(SecurityConfig.class)
class IdentityMappingEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityVaultService vaultService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String jwt(List<String> permissions) {
        return Jwts.builder()
                .setSubject(UUID.randomUUID().toString())
                .claim("permissions", permissions)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void mapIsPublicAndReturnsTheAnonymousId() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("ana@icesi.edu.co")).thenReturn(anonymousId);

        mockMvc.perform(post("/api/v1/identities/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realIdentity\":\"ana@icesi.edu.co\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()));
    }

    @Test
    void visitorIsRegisteredWithAllItsDataInTheVault() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("VISITOR|ana@mail.com|Ana|Entrevista")).thenReturn(anonymousId);

        mockMvc.perform(post("/api/v1/identities/visitor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana\",\"email\":\"ana@mail.com\",\"reason_for_visit\":\"Entrevista\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()));
    }

    @Test
    void lookupWithRealJwtAndPermissionResolvesAndAudits() throws Exception {
        UUID target = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(target)).thenReturn("ana@icesi.edu.co");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", target)
                        .header("Authorization", "Bearer " + jwt(List.of("identity:lookup"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realIdentity").value("ana@icesi.edu.co"));

        verify(kafkaTemplate).send(eq("audit.identity.accessed"), any());
    }

    @Test
    void lookupWithRealJwtWithoutPermissionIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/identities/lookup/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwt(List.of("symptom:report"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void tamperedJwtIsTreatedAsAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/identities/lookup/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwt(List.of("identity:lookup")) + "x"))
                .andExpect(status().isUnauthorized());
    }
}
