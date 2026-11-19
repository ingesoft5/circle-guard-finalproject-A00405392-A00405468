package com.circleguard.promotion.listener;

import com.circleguard.promotion.metrics.PromotionMetrics;
import com.circleguard.promotion.service.HealthStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SurveyListenerTest {

    @Mock
    private HealthStatusService healthStatusService;

    @Mock
    private PromotionMetrics metrics;

    @InjectMocks
    private SurveyListener surveyListener;

    @Test
    void onSurveySubmitted_WithSymptoms_PromotesToSuspect() {
        Map<String, Object> event = Map.of(
            "anonymousId", "tester-123",
            "hasSymptoms", true
        );

        surveyListener.onSurveySubmitted(event);

        verify(healthStatusService).updateStatus("tester-123", "SUSPECT");
        verifyNoInteractions(metrics);   // sin marca de tiempo no se mide la contención
    }

    @Test
    void onSurveySubmitted_WithSymptoms_RecordsContainmentTimeFromTheFormTimestamp() {
        long reportedAt = System.currentTimeMillis() - 1_500;
        Map<String, Object> event = Map.of("anonymousId", "tester-123", "hasSymptoms", true, "timestamp", reportedAt);

        surveyListener.onSurveySubmitted(event);

        verify(metrics).containedSince(reportedAt);
    }

    @Test
    void onSurveySubmitted_WithoutSymptoms_DoesNothing() {
        Map<String, Object> event = Map.of(
            "anonymousId", "tester-123",
            "hasSymptoms", false
        );

        surveyListener.onSurveySubmitted(event);

        verify(healthStatusService, never()).updateStatus(anyString(), anyString());
    }
}
