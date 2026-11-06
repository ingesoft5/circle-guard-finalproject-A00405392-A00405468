package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.Questionnaire;
import com.circleguard.form.model.ValidationStatus;
import com.circleguard.form.repository.HealthSurveyRepository;
import com.circleguard.form.repository.QuestionnaireRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FormServicesTest {

    private final HealthSurveyRepository surveyRepository = mock(HealthSurveyRepository.class);
    private final QuestionnaireRepository questionnaireRepository = mock(QuestionnaireRepository.class);
    private final SymptomMapper symptomMapper = mock(SymptomMapper.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final QuestionnaireService questionnaireService = new QuestionnaireService(questionnaireRepository);
    private final HealthSurveyService surveyService =
            new HealthSurveyService(surveyRepository, questionnaireService, symptomMapper, kafka);

    // ------------------------------------------------------------- HealthSurveyService

    @Test
    @SuppressWarnings("unchecked")
    void submittedSurveyWithSymptomsIsSavedAndPublishedToPromotion() {
        UUID anonymousId = UUID.randomUUID();
        Questionnaire active = Questionnaire.builder().title("Salud").version(1).isActive(true).build();
        HealthSurvey survey = HealthSurvey.builder().anonymousId(anonymousId).responses(Map.of("q1", "YES")).build();
        when(questionnaireRepository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.of(active));
        when(symptomMapper.hasSymptoms(survey, active)).thenReturn(true);
        when(surveyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey saved = surveyService.submitSurvey(survey);

        assertThat(saved.getHasFever()).isTrue();
        assertThat(saved.getHasCough()).isTrue();
        assertThat(saved.getValidationStatus()).isNull();
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq("survey.submitted"), eq(anonymousId.toString()), event.capture());
        assertThat((Map<String, Object>) event.getValue())
                .containsEntry("anonymousId", anonymousId)
                .containsEntry("hasSymptoms", true);
    }

    @Test
    void surveyWithoutActiveQuestionnaireKeepsExplicitAnswersAndMarksAttachmentPending() {
        HealthSurvey survey = HealthSurvey.builder().anonymousId(UUID.randomUUID())
                .hasFever(true).hasCough(false).attachmentPath("certificado.pdf").build();
        when(questionnaireRepository.findFirstByIsActiveTrueOrderByVersionDesc()).thenReturn(Optional.empty());
        when(surveyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        HealthSurvey saved = surveyService.submitSurvey(survey);

        assertThat(saved.getHasFever()).isTrue();
        assertThat(saved.getHasCough()).isFalse();
        assertThat(saved.getValidationStatus()).isEqualTo(ValidationStatus.PENDING);
        verifyNoInteractions(symptomMapper);
    }

    @Test
    void approvedCertificateIsSavedAndNotifiesPromotion() {
        UUID surveyId = UUID.randomUUID(), adminId = UUID.randomUUID(), anonymousId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().id(surveyId).anonymousId(anonymousId).build();
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));

        surveyService.validateSurvey(surveyId, ValidationStatus.APPROVED, adminId);

        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.APPROVED);
        assertThat(survey.getValidatedBy()).isEqualTo(adminId);
        verify(surveyRepository).save(survey);
        verify(kafka).send(eq("certificate.validated"), eq(anonymousId.toString()), any());
    }

    @Test
    void rejectedCertificateIsSavedWithoutEvent() {
        UUID surveyId = UUID.randomUUID();
        HealthSurvey survey = HealthSurvey.builder().id(surveyId).anonymousId(UUID.randomUUID()).build();
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));

        surveyService.validateSurvey(surveyId, ValidationStatus.REJECTED, UUID.randomUUID());

        assertThat(survey.getValidationStatus()).isEqualTo(ValidationStatus.REJECTED);
        verifyNoInteractions(kafka);
    }

    @Test
    void validatingUnknownSurveyFails() {
        UUID surveyId = UUID.randomUUID();
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> surveyService.validateSurvey(surveyId, ValidationStatus.APPROVED, UUID.randomUUID()))
                .hasMessageContaining(surveyId.toString());
    }

    @Test
    void pendingSurveysAreThoseWithAttachmentAwaitingValidation() {
        List<HealthSurvey> pending = List.of(HealthSurvey.builder().attachmentPath("a.pdf").build());
        when(surveyRepository.findByAttachmentPathIsNotNullAndValidationStatus(ValidationStatus.PENDING)).thenReturn(pending);

        assertThat(surveyService.getPendingSurveys()).isSameAs(pending);
    }

    // ------------------------------------------------------------ QuestionnaireService

    @Test
    void savingQuestionnaireLinksEveryQuestionToIt() {
        Question q1 = Question.builder().text("¿Fiebre?").orderIndex(1).build();
        Question q2 = Question.builder().text("¿Tos?").orderIndex(2).build();
        Questionnaire questionnaire = Questionnaire.builder().title("Salud").questions(List.of(q1, q2)).build();
        when(questionnaireRepository.save(questionnaire)).thenReturn(questionnaire);

        assertThat(questionnaireService.saveQuestionnaire(questionnaire)).isSameAs(questionnaire);
        assertThat(q1.getQuestionnaire()).isSameAs(questionnaire);
        assertThat(q2.getQuestionnaire()).isSameAs(questionnaire);
    }

    @Test
    void activatingAQuestionnaireDeactivatesTheOthers() {
        UUID targetId = UUID.randomUUID();
        Questionnaire old = Questionnaire.builder().id(UUID.randomUUID()).isActive(true).build();
        Questionnaire target = Questionnaire.builder().id(targetId).isActive(false).build();
        when(questionnaireRepository.findAll()).thenReturn(List.of(old, target));
        when(questionnaireRepository.findById(targetId)).thenReturn(Optional.of(target));

        questionnaireService.activateQuestionnaire(targetId);

        assertThat(old.getIsActive()).isFalse();
        assertThat(target.getIsActive()).isTrue();
        verify(questionnaireRepository).save(old);
        verify(questionnaireRepository).save(target);
        assertThat(questionnaireService.getAllQuestionnaires()).containsExactly(old, target);
    }

    // ---------------------------------------------------------------- StorageService

    @Test
    void storedFileKeepsItsNameWithAUniquePrefix() throws Exception {
        StorageService storage = new StorageService();
        String name = storage.store(new MockMultipartFile("file", "certificado.pdf", "application/pdf", "pdf".getBytes()));

        assertThat(name).endsWith("_certificado.pdf");
        Path stored = Path.of("/tmp/circleguard-uploads").resolve(name);
        assertThat(Files.readString(stored)).isEqualTo("pdf");
        Files.deleteIfExists(stored);
    }

    @Test
    void storageErrorsAreReported() throws Exception {
        MockMultipartFile broken = spy(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]));
        doThrow(new java.io.IOException("disco lleno")).when(broken).getInputStream();

        assertThatThrownBy(() -> new StorageService().store(broken)).hasMessageContaining("disco lleno");
    }
}
