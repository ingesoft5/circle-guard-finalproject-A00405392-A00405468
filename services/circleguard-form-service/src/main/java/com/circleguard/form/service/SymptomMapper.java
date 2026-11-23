package com.circleguard.form.service;

import com.circleguard.form.model.HealthSurvey;
import com.circleguard.form.model.Questionnaire;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SymptomMapper {

    // Palabras de las preguntas que indican un síntoma si la respuesta es SÍ. El cuestionario de la app
    // está en español: antes solo se buscaban en inglés y ningún cuestionario real detectaba síntomas
    // (encontrado en HU-20 al medir las promociones). Sin tildes, porque el texto se normaliza.
    private static final List<String> SYMPTOM_WORDS =
            List.of("fever", "cough", "breathing", "fiebre", "tos", "respirar", "respiracion");
    private static final List<String> SYMPTOM_LIST_WORDS = List.of("symptoms", "sintomas");

    /**
     * Determines if the survey responses indicate symptoms.
     * Logic: If any question containing keywords (fever, cough) has a 'YES' response.
     */
    public boolean hasSymptoms(HealthSurvey survey, Questionnaire questionnaire) {
        if (survey.getResponses() == null || questionnaire == null || questionnaire.getQuestions() == null) {
            return false;
        }

        Map<String, Object> responses = survey.getResponses();
        
        return questionnaire.getQuestions().stream()
                .filter(q -> responses.containsKey(q.getId().toString()))
                .anyMatch(q -> {
                    String answer = String.valueOf(responses.get(q.getId().toString()));
                    String text = normalize(q.getText());
                    
                    if ("YES".equalsIgnoreCase(answer)) {
                        return containsAnyWord(text, SYMPTOM_WORDS);
                    }
                    
                    // For multi-choice, if anything is selected (assuming selecting anything indicates symptoms)
                    if (q.getType().toString().contains("CHOICE") && answer != null && !answer.isEmpty() && !"[]".equals(answer)) {
                        return containsAnyWord(text, SYMPTOM_LIST_WORDS);
                    }
                    
                    return false;
                });
    }

    /** Minúsculas y sin tildes: "¿Tienes TOS o dificultad para respirar?" -> "¿tienes tos o dificultad para respirar?". */
    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    /** Palabra completa, para que "tos" no coincida dentro de "datos" o "todos". */
    private static boolean containsAnyWord(String text, List<String> words) {
        return words.stream().anyMatch(w -> java.util.regex.Pattern.compile("\\b" + w + "\\b").matcher(text).find());
    }
}
