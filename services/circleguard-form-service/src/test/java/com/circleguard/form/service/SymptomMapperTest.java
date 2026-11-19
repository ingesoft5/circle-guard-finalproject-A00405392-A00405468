package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Question;
import com.circleguard.form.model.QuestionType;
import com.circleguard.form.model.Questionnaire;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SymptomMapperTest {

    private final SymptomMapper mapper = new SymptomMapper();

    @Test
    void shouldDetectSymptomsFromFever() {
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();
        
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();
        
        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "YES"))
                .build();
        
        assertTrue(mapper.hasSymptoms(survey, questionnaire));
    }

    @Test
    void shouldNotDetectSymptomsWhenNo() {
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder()
                .id(questionId)
                .text("Do you have a fever?")
                .type(QuestionType.YES_NO)
                .build();
        
        Questionnaire questionnaire = Questionnaire.builder()
                .questions(List.of(q))
                .build();
        
        HealthSurvey survey = HealthSurvey.builder()
                .responses(Map.of(questionId.toString(), "NO"))
                .build();
        
        assertFalse(mapper.hasSymptoms(survey, questionnaire));
    }

    // HU-20: el cuestionario de la app está en español; antes ninguna de estas respuestas contaba como síntoma.
    private boolean answered(String questionText, QuestionType type, String answer) {
        UUID id = UUID.randomUUID();
        Question q = Question.builder().id(id).text(questionText).type(type).build();
        Questionnaire questionnaire = Questionnaire.builder().questions(List.of(q)).build();
        HealthSurvey survey = HealthSurvey.builder().responses(Map.of(id.toString(), answer)).build();
        return mapper.hasSymptoms(survey, questionnaire);
    }

    @Test
    void detectsSymptomsInTheSpanishQuestionnaireOfTheApp() {
        assertTrue(answered("¿Tienes fiebre (38 °C o más)?", QuestionType.YES_NO, "YES"));
        assertTrue(answered("¿Tienes TOS o dificultad para respirar?", QuestionType.YES_NO, "YES"));
        assertTrue(answered("¿Tienes dificultad para la respiración?", QuestionType.YES_NO, "YES"));
        assertFalse(answered("¿Tienes fiebre (38 °C o más)?", QuestionType.YES_NO, "NO"));
    }

    @Test
    void keywordsMatchWholeWordsOnly() {
        assertFalse(answered("¿Actualizaste tus datos de contacto?", QuestionType.YES_NO, "YES"));   // "datos" no es "tos"
        assertFalse(answered("¿Todos en tu casa están bien?", QuestionType.YES_NO, "YES"));
    }
}
