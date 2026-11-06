package com.circleguard.form.controller;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.service.HealthSurveyService;
import com.circleguard.form.service.QuestionnaireService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CertificateValidationController.class, QuestionnaireController.class})
class CertificateValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HealthSurveyService surveyService;

    @MockBean
    private QuestionnaireService questionnaireService;

    @Test
    void listsPendingCertificates() throws Exception {
        when(surveyService.getPendingSurveys()).thenReturn(List.of(HealthSurvey.builder().attachmentPath("a.pdf").build()));

        mockMvc.perform(get("/api/v1/certificates/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].attachmentPath").value("a.pdf"));
    }

    @Test
    void validatesACertificate() throws Exception {
        UUID surveyId = UUID.randomUUID(), adminId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/certificates/{id}/validate", surveyId)
                        .param("status", "APPROVED").param("adminId", adminId.toString()))
                .andExpect(status().isOk());

        verify(surveyService).validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);
    }

    @Test
    void listsCreatesAndActivatesQuestionnaires() throws Exception {
        Questionnaire saved = Questionnaire.builder().title("Salud").version(1).build();
        when(questionnaireService.getAllQuestionnaires()).thenReturn(List.of(saved));
        when(questionnaireService.saveQuestionnaire(org.mockito.ArgumentMatchers.any())).thenReturn(saved);
        UUID id = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/questionnaires"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Salud"));
        mockMvc.perform(post("/api/v1/questionnaires").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Salud\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Salud"));
        mockMvc.perform(post("/api/v1/questionnaires/{id}/activate", id))
                .andExpect(status().isOk());

        verify(questionnaireService).activateQuestionnaire(id);
    }
}
