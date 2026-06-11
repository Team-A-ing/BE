package com.readb.service.analysis;

import com.readb.domain.analysis.Analysis;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.dto.leader.LeaderGrowthResponse;
import com.readb.repository.AnalysisRepository;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaderGrowthService {

    private static final int TREND_MONTHS = 6;

    private final MeetingRepository meetingRepository;
    private final AnalysisRepository analysisRepository;
    private final PromiseRepository promiseRepository;

    @Transactional(readOnly = true)
    public LeaderGrowthResponse getLeaderGrowth(Long leaderId) {
        List<Meeting> meetings = meetingRepository.findByLeaderIdOrderByCreatedAtDesc(leaderId);
        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();

        Map<Long, Analysis> analysisByMeeting = meetingIds.isEmpty() ? Map.of()
                : analysisRepository.findByMeetingIdIn(meetingIds)
                        .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));

        // 최근 6개월 월별 집계 — 분석 완료된 미팅이 있는 달만 포함
        YearMonth from = YearMonth.now().minusMonths(TREND_MONTHS - 1);
        Map<YearMonth, List<Analysis>> byMonth = new TreeMap<>();
        for (Meeting m : meetings) {
            YearMonth ym = YearMonth.from(m.getCreatedAt());
            if (ym.isBefore(from)) continue;
            Analysis a = analysisByMeeting.get(m.getId());
            if (a == null) continue;
            byMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(a);
        }

        List<LeaderGrowthResponse.MonthlyTrendPoint> talkRatioTrend = new ArrayList<>();
        List<LeaderGrowthResponse.MonthlyTrendPoint> safetyTrend = new ArrayList<>();
        for (Map.Entry<YearMonth, List<Analysis>> e : byMonth.entrySet()) {
            String month = e.getKey().toString();

            List<Integer> ratios = e.getValue().stream()
                    .map(Analysis::getTalkRatio)
                    .filter(Objects::nonNull)
                    .map(tr -> tr.get("leaderRatio"))
                    .filter(Objects::nonNull)
                    .map(v -> ((Number) v).intValue())
                    .toList();
            if (!ratios.isEmpty()) {
                double avg = ratios.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                talkRatioTrend.add(new LeaderGrowthResponse.MonthlyTrendPoint(
                        month, Math.round(avg * 10.0) / 10.0, ratios.size()));
            }

            List<Double> safeties = e.getValue().stream()
                    .map(Analysis::getSafetyScore)
                    .filter(Objects::nonNull)
                    .toList();
            if (!safeties.isEmpty()) {
                double avg = safeties.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                safetyTrend.add(new LeaderGrowthResponse.MonthlyTrendPoint(
                        month, Math.round(avg * 10.0) / 10.0, safeties.size()));
            }
        }

        // 리더 본인이 등록한 약속 이행 통계
        List<Promise> promises = promiseRepository.findByOwnerIdOrderByCreatedAtDesc(leaderId);
        int total = promises.size();
        int done = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.DONE).count();
        int missed = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.MISSED).count();
        int pending = total - done - missed;
        double doneRate = total == 0 ? 0.0 : Math.round((double) done / total * 1000.0) / 10.0;
        LeaderGrowthResponse.PromiseStats promiseStats =
                new LeaderGrowthResponse.PromiseStats(total, done, missed, pending, doneRate);

        return new LeaderGrowthResponse(talkRatioTrend, safetyTrend, promiseStats,
                buildHighlights(talkRatioTrend, safetyTrend, promiseStats));
    }

    // Fact-Based 원칙: 관찰된 수치 변화만 서술, 해석 라벨 없음
    private List<String> buildHighlights(List<LeaderGrowthResponse.MonthlyTrendPoint> talkRatioTrend,
                                         List<LeaderGrowthResponse.MonthlyTrendPoint> safetyTrend,
                                         LeaderGrowthResponse.PromiseStats stats) {
        List<String> highlights = new ArrayList<>();
        if (talkRatioTrend.size() >= 2) {
            double first = talkRatioTrend.get(0).value();
            double last = talkRatioTrend.get(talkRatioTrend.size() - 1).value();
            double diff = Math.round((last - first) * 10.0) / 10.0;
            highlights.add(String.format("리더 발화 비율 %.0f%% → %.0f%% (%s%.1f%%p)",
                    first, last, diff >= 0 ? "+" : "", diff));
        }
        if (safetyTrend.size() >= 2) {
            double first = safetyTrend.get(0).value();
            double last = safetyTrend.get(safetyTrend.size() - 1).value();
            double diff = Math.round((last - first) * 10.0) / 10.0;
            highlights.add(String.format("팀 평균 안전감 신호 %.1f → %.1f (%s%.1f)",
                    first, last, diff >= 0 ? "+" : "", diff));
        }
        if (stats.total() > 0) {
            highlights.add(String.format("등록한 약속 %d건 중 %d건 이행 (이행률 %.0f%%)",
                    stats.total(), stats.done(), stats.doneRate()));
        }
        return highlights;
    }
}
