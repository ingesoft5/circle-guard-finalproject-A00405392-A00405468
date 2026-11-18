package com.circleguard.promotion.controller;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import com.circleguard.promotion.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HU-19: estado de una persona para el cache-aside del gateway. */
@WebMvcTest(InternalStatusController.class)
@Import(SecurityConfig.class)
class InternalStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserNodeRepository userNodeRepository;

    @Test
    void returnsTheStatusStoredInTheGraph() throws Exception {
        when(userNodeRepository.findStatusByAnonymousId("abc")).thenReturn(Optional.of("PROBABLE"));

        mockMvc.perform(get("/internal/v1/users/abc/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value("abc"))
                .andExpect(jsonPath("$.status").value("PROBABLE"));
    }

    @Test
    void personNotInTheGraphIsActive() throws Exception {
        when(userNodeRepository.findStatusByAnonymousId("nuevo")).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/v1/users/nuevo/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
