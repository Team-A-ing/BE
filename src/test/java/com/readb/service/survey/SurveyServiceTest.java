package com.readb.service.survey;

import com.readb.domain.survey.Survey;
import com.readb.dto.survey.SurveyHistoryResponse;
import com.readb.repository.SurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "submittedAt"));
        LocalDateTime t1 = LocalDateTime.now().minusDays(2);
        LocalDateTime t2 = LocalDateTime.now().minusDays(1);
        Survey s1 = Survey.builder().meetingId(10L).memberId(1L).scores(Map.of("energyLevel", 4)).submittedAt(t1).build();
        Survey s2 = Survey.builder().meetingId(20L).memberId(1L).scores(Map.of("energyLevel", 2)).submittedAt(t2).build();

        when(surveyRepository.findByMemberId(1L, pageable)).thenReturn(new PageImpl<>(List.of(s2, s1), pageable, 2));

        Page<SurveyHistoryResponse> result = surveyService.getHistory(1L, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).meetingId()).isEqualTo(20L);
        assertThat(result.getContent().get(1).meetingId()).isEqualTo(10L);
        assertThat(result.getTotalElements()).isEqualTo(2);
        verify(surveyRepository).findByMemberId(1L, pageable);
    }

    @Test
    void getHistoryReturnsEmptyPageWhenNoSurveys() {
        Pageable pageable = PageRequest.of(0, 20);
        when(surveyRepository.findByMemberId(99L, pageable)).thenReturn(Page.empty(pageable));

        Page<SurveyHistoryResponse> result = surveyService.getHistory(99L, pageable);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void getHistoryMapsScoresCorrectly() {
        Pageable pageable = PageRequest.of(0, 20);
        Map<String, Object> scores = Map.of("energyLevel", 3, "issues", List.of("업무 블로커"));
        Survey survey = Survey.builder().meetingId(5L).memberId(1L).scores(scores).submittedAt(LocalDateTime.now()).build();

        when(surveyRepository.findByMemberId(1L, pageable)).thenReturn(new PageImpl<>(List.of(survey)));

        Page<SurveyHistoryResponse> result = surveyService.getHistory(1L, pageable);

        assertThat(result.getContent().get(0).scores()).isEqualTo(scores);
    }
}
