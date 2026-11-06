package com.circleguard.auth.controller;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import com.circleguard.auth.service.QrTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UserController.class, QrTokenController.class})
class UserAndQrControllerTest {

    private static final String ANON_ID = "3f2b8c1e-5d4a-4b6f-9e7d-1a2b3c4d5e6f";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalUserRepository userRepository;

    @MockBean
    private QrTokenService qrTokenService;

    @Test
    @WithMockUser
    void listsUsersWithAPermissionAndToleratesMissingEmail() throws Exception {
        when(userRepository.findUsersByPermissionName("alert:receive_priority")).thenReturn(List.of(
                LocalUser.builder().username("health_user").email("health@circleguard.edu").build(),
                LocalUser.builder().username("sin_correo").email(null).build()));

        mockMvc.perform(get("/api/v1/users/permissions/alert:receive_priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("health_user"))
                .andExpect(jsonPath("$[0].email").value("health@circleguard.edu"))
                .andExpect(jsonPath("$[1].email").value(""));
    }

    @Test
    @WithMockUser(username = ANON_ID)
    void generatesQrForTheAuthenticatedAnonymousId() throws Exception {
        when(qrTokenService.generateQrToken(UUID.fromString(ANON_ID))).thenReturn("qr-firmado");

        mockMvc.perform(get("/api/v1/auth/qr/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value("qr-firmado"))
                .andExpect(jsonPath("$.expiresIn").exists());
    }

    @Test
    void qrRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/qr/generate"))
                .andExpect(status().isUnauthorized());
    }
}
