package com.readb.service.survey;

import com.readb.domain.survey.Survey;
import com.readb.dto.survey.SurveyHistoryResponse;
import com.readb.repository.SurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    @Mock
    private SurveyRepository surveyRepository;

    private SurveyService surveyService;

    @BeforeEach
    void setUp() {
        surveyService = new SurveyService(surveyRepository);
    }

    @Test
    void getHistoryReturnsSurveysOrderedBySubmittedAt() {
        LocalDateTime t1 = LocalDateTime.now().minusDays(2);
        LocalDateTime t2 = LocalDateTime.now().minusDays(1);
        Survey s1 = Survey.builder()
                .meetingId(10L).memberId(1L)
                .scores(Map.of("energyLevel", 4))
                .submittedAt(t1).build();
        Survey s2 = Survey.builder()
                .meetingId(20L).memberId(1L)
                .scores(Map.of("energyLevel", 2))
                .submittedAt(t2).build();

        when(surveyRepository.findByMemberIdOrderBySubmittedAtDesc(1L)).thenReturn(List.of(s2, s1));

        List<SurveyHistoryResponse> result = surveyService.getHistory(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).meetingId()).isEqualTo(20L);
        assertThat(result.get(0).submittedAt()).isEqualTo(t2);
        assertThat(result.get(1).meetingId()).isEqualTo(10L);
        verify(surveyRepository).findByMemberIdOrderBySubmittedAtDesc(1L);
    }

    @Test
    void getHistoryReturnsEmptyWhenNoSurveys() {
        when(surveyRepository.findByMemberIdOrderBySubmittedAtDesc(99L)).thenReturn(List.of());

        List<SurveyHistoryResponse> result = surveyService.getHistory(99L);

        assertThat(result).isEmpty();
    }

    @Test
    void getHistoryMapsScoresCorrectly() {
        Map<String, Object> scores = Map.of("energyLevel", 3, "issues", List.of("업무 블로커"));
        Survey survey = Survey.builder()
                .meetingId(5L).memberId(1L)
                .scores(scores)
                .submittedAt(LocalDateTime.now()).build();

        when(surveyRepository.findByMemberIdOrderBySubmittedAtDesc(1L)).thenReturn(List.of(survey));

        List<SurveyHistoryResponse> result = surveyService.getHistory(1L);

        assertThat(result.get(0).scores()).isEqualTo(scores);
    }
}
