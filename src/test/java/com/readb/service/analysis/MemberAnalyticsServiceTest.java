package com.readb.service.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.readb.adapter.llm.LlmAdapter;
import com.readb.domain.analysis.Analysis;
import com.readb.domain.meeting.Meeting;
import com.readb.dto.analysis.CareerMemoryResponse;
import com.readb.dto.analysis.PortfolioResponse;
import com.readb.dto.analysis.SpeechTrendResponse;
import com.readb.repository.AnalysisRepository;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import com.readb.repository.RecordingRepository;
import com.readb.repository.SurveyRepository;
import com.readb.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAnalyticsServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private AnalysisRepository analysisRepository;

    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(
                analysisRepository,
                meetingRepository,
                mock(SurveyRepository.class),
                mock(RecordingRepository.class),
                mock(PromiseRepository.class),
                mock(UserRepository.class),
                mock(LlmAdapter.class),
                mock(LlmAdapter.class),
                new ObjectMapper()
        );
    }

    private final LocalDateTime t1 = LocalDateTime.now().minusDays(7);
    private final LocalDateTime t2 = LocalDateTime.now().minusDays(1);

    private Meeting meeting(long id, LocalDateTime at) {
        return Meeting.builder().id(id).memberId(1L).scheduledAt(at).title("meeting-" + id).build();
    }

    private Analysis analysis(long meetingId, List<String> tags, Map<String, Object> speechActs, Double score) {
        return Analysis.builder()
                .meetingId(meetingId)
                .careerTags(tags)
                .memberFeedback(Map.of("summary", "feedback-" + meetingId))
                .speechActs(speechActs)
                .safetyScore(score)
                .build();
    }

    // ② getCareerMemory 실 데이터 테스트

    @Test
    void getCareerMemoryReturnsMeetingsWithAnalysis() {
        Meeting m1 = meeting(1L, t1);
        Meeting m2 = meeting(2L, t2);
        Analysis a1 = analysis(1L, List.of("주도성"), Map.of(), 70.0);
        Analysis a2 = analysis(2L, List.of("협업", "성장"), Map.of(), 80.0);

        when(meetingRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(m2, m1));
        when(analysisRepository.findByMeetingIdIn(List.of(2L, 1L))).thenReturn(List.of(a1, a2));

        List<CareerMemoryResponse> result = analysisService.getCareerMemory(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).meetingId()).isEqualTo(2L);
        assertThat(result.get(0).careerTags()).containsExactly("협업", "성장");
    }

    @Test
    void getCareerMemoryReturnsEmptyWhenNoMeetings() {
        when(meetingRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
        assertThat(analysisService.getCareerMemory(1L)).isEmpty();
    }

    @Test
    void getCareerMemorySkipsMeetingsWithoutAnalysis() {
        Meeting m1 = meeting(1L, t1);
        Meeting m2 = meeting(2L, t2);
        Analysis a1 = analysis(1L, List.of("주도성"), Map.of(), 70.0);

        when(meetingRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(m2, m1));
        when(analysisRepository.findByMeetingIdIn(List.of(2L, 1L))).thenReturn(List.of(a1));

        List<CareerMemoryResponse> result = analysisService.getCareerMemory(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).meetingId()).isEqualTo(1L);
    }

    // ③ getSpeechTrend 테스트

    @Test
    void getSpeechTrendCountsEachCategory() {
        Meeting m = meeting(1L, t1);
        Map<String, Object> acts = Map.of(
                "vulnerability", List.of(Map.of("text", "a"), Map.of("text", "b")),
                "dissent", List.of(Map.of("text", "c")),
                "initiative", List.of()
        );
        Analysis a = analysis(1L, List.of(), acts, 60.0);

        when(meetingRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(m));
        when(analysisRepository.findByMeetingIdIn(List.of(1L))).thenReturn(List.of(a));

        List<SpeechTrendResponse> result = analysisService.getSpeechTrend(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vulnerabilityCount()).isEqualTo(2);
        assertThat(result.get(0).dissentCount()).isEqualTo(1);
        assertThat(result.get(0).initiativeCount()).isEqualTo(0);
    }

    @Test
    void getSpeechTrendHandlesNullSpeechActs() {
        Meeting m = meeting(1L, t1);
        Analysis a = Analysis.builder().meetingId(1L).speechActs(null).build();

        when(meetingRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(m));
        when(analysisRepository.findByMeetingIdIn(List.of(1L))).thenReturn(List.of(a));

        List<SpeechTrendResponse> result = analysisService.getSpeechTrend(1L);

        assertThat(result.get(0).vulnerabilityCount()).isEqualTo(0);
        assertThat(result.get(0).dissentCount()).isEqualTo(0);
        assertThat(result.get(0).initiativeCount()).isEqualTo(0);
    }

    // ① getPortfolio 테스트

    @Test
    void getPortfolioAggregatesCareerTagsByFrequency() {
        Meeting m1 = meeting(1L, t1);
        Meeting m2 = meeting(2L, t2);
        Analysis a1 = analysis(1L, List.of("주도성", "협업"), Map.of(), 70.0);
        Analysis a2 = analysis(2L, List.of("주도성", "성장"), Map.of(), 80.0);

        when(meetingRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(m2, m1));
        when(analysisRepository.findByMeetingIdIn(List.of(2L, 1L))).thenReturn(List.of(a1, a2));

        PortfolioResponse result = analysisService.getPortfolio(1L);

        assertThat(result.meetingHistory()).hasSize(2);
        assertThat(result.scoreTrend()).hasSize(2);
        assertThat(result.topCareerTags().get(0)).isEqualTo("주도성");
        assertThat(result.feedbackSummaries()).containsExactlyInAnyOrder("feedback-1", "feedback-2");
    }

    @Test
    void getPortfolioReturnsEmptyWhenNoMeetings() {
        when(meetingRepository.findByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        PortfolioResponse result = analysisService.getPortfolio(1L);

        assertThat(result.meetingHistory()).isEmpty();
        assertThat(result.scoreTrend()).isEmpty();
        assertThat(result.topCareerTags()).isEmpty();
    }
}
