package com.readb.service.analysis;

import com.readb.domain.actionplan.ActionPlan;
import com.readb.domain.analysis.Analysis;
import com.readb.domain.meeting.Meeting;
import com.readb.domain.promise.Promise;
import com.readb.domain.promise.PromiseStatus;
import com.readb.domain.user.User;
import com.readb.domain.user.UserRole;
import com.readb.dto.leader.LeaderGrowthResponse;
import com.readb.repository.ActionPlanRepository;
import com.readb.repository.AnalysisRepository;
import com.readb.repository.MeetingRepository;
import com.readb.repository.PromiseRepository;
import com.readb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private final ActionPlanRepository actionPlanRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public LeaderGrowthResponse getLeaderGrowth(Long leaderId) {
        List<Meeting> meetings = meetingRepository.findByLeaderIdOrderByCreatedAtDesc(leaderId);
        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();

        Map<Long, Analysis> analysisByMeeting = meetingIds.isEmpty() ? Map.of()
                : analysisRepository.findByMeetingIdIn(meetingIds)
                        .stream().collect(Collectors.toMap(Analysis::getMeetingId, a -> a));

        // 최근 6개월 월별 집계
        YearMonth from = YearMonth.now().minusMonths(TREND_MONTHS - 1);
        Map<YearMonth, List<Analysis>> analyzedByMonth = new TreeMap<>();
        Map<YearMonth, Integer> meetingCountByMonth = new TreeMap<>();
        for (Meeting m : meetings) {
            YearMonth ym = YearMonth.from(m.getCreatedAt());
            if (ym.isBefore(from)) continue;
            meetingCountByMonth.merge(ym, 1, Integer::sum);
            Analysis a = analysisByMeeting.get(m.getId());
            if (a == null) continue;
            analyzedByMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(a);
        }

        List<LeaderGrowthResponse.MonthlyTrendPoint> talkRatioTrend = new ArrayList<>();
        List<LeaderGrowthResponse.MonthlyTrendPoint> safetyTrend = new ArrayList<>();
        for (Map.Entry<YearMonth, List<Analysis>> e : analyzedByMonth.entrySet()) {
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

        // 월별 1on1 운영 횟수 (분석 여부 무관 — 규칙성 지표)
        List<LeaderGrowthResponse.MonthlyTrendPoint> monthlyMeetings = meetingCountByMonth.entrySet().stream()
                .map(e -> new LeaderGrowthResponse.MonthlyTrendPoint(
                        e.getKey().toString(), e.getValue(), e.getValue()))
                .toList();

        // 리더 본인이 등록한 약속 이행 통계
        List<Promise> promises = promiseRepository.findByOwnerIdOrderByCreatedAtDesc(leaderId);
        int total = promises.size();
        int done = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.DONE).count();
        int missed = (int) promises.stream().filter(p -> p.getStatus() == PromiseStatus.MISSED).count();
        int pending = total - done - missed;
        double doneRate = total == 0 ? 0.0 : Math.round((double) done / total * 1000.0) / 10.0;
        LeaderGrowthResponse.PromiseStats promiseStats =
                new LeaderGrowthResponse.PromiseStats(total, done, missed, pending, doneRate);

        // 코칭 실행률: 시스템이 제안한 액션 플랜 중 완료 비율 (리더의 코칭 수용성)
        List<ActionPlan> plans = meetingIds.isEmpty() ? List.of()
                : actionPlanRepository.findByMeetingIdInOrderByIdAsc(meetingIds);
        int planTotal = plans.size();
        int planCompleted = (int) plans.stream().filter(ActionPlan::isCompleted).count();
        double executionRate = planTotal == 0 ? 0.0
                : Math.round((double) planCompleted / planTotal * 1000.0) / 10.0;

        // 이행/미이행 항목 목록 (어느 멤버 액션인지 함께) — 코칭 실행률 펼침 보기용.
        // 액션 플랜이 없으면 유저 조회 자체를 건너뛰고, 있을 때만 '액션 플랜이 달린 미팅'의 멤버만 조회한다.
        List<LeaderGrowthResponse.CoachingPlanItem> completedItems = List.of();
        List<LeaderGrowthResponse.CoachingPlanItem> pendingItems = List.of();
        if (!plans.isEmpty()) {
            Map<Long, Long> memberIdByMeeting = meetings.stream()
                    .collect(Collectors.toMap(Meeting::getId, Meeting::getMemberId));
            List<Long> planMemberIds = plans.stream()
                    .map(p -> memberIdByMeeting.get(p.getMeetingId()))
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, String> memberNameById = userRepository.findAllById(planMemberIds)
                    .stream().collect(Collectors.toMap(User::getId, User::getName));
            java.util.function.Function<ActionPlan, LeaderGrowthResponse.CoachingPlanItem> toItem = p ->
                    new LeaderGrowthResponse.CoachingPlanItem(
                            p.getId(), p.getContent(),
                            memberNameById.getOrDefault(memberIdByMeeting.get(p.getMeetingId()), ""));
            completedItems = plans.stream().filter(ActionPlan::isCompleted).map(toItem).toList();
            pendingItems = plans.stream().filter(p -> !p.isCompleted()).map(toItem).toList();
        }
        LeaderGrowthResponse.CoachingExecution coachingExecution =
                new LeaderGrowthResponse.CoachingExecution(planTotal, planCompleted, executionRate,
                        completedItems, pendingItems);

        return new LeaderGrowthResponse(
                buildKeyInsight(talkRatioTrend, safetyTrend),
                talkRatioTrend, safetyTrend, monthlyMeetings,
                promiseStats, coachingExecution,
                buildMemberCadence(leaderId, meetings),
                buildHighlights(talkRatioTrend, safetyTrend, promiseStats));
    }

    // 행동(발화 비율)과 결과(팀 신호)를 한 문장으로 연결 — 두 추이 모두 2개월 이상일 때만
    private String buildKeyInsight(List<LeaderGrowthResponse.MonthlyTrendPoint> talkRatioTrend,
                                   List<LeaderGrowthResponse.MonthlyTrendPoint> safetyTrend) {
        if (talkRatioTrend.size() < 2 || safetyTrend.size() < 2) return null;
        double rFirst = talkRatioTrend.get(0).value();
        double rLast = talkRatioTrend.get(talkRatioTrend.size() - 1).value();
        double sFirst = safetyTrend.get(0).value();
        double sLast = safetyTrend.get(safetyTrend.size() - 1).value();
        double sDiff = Math.round((sLast - sFirst) * 10.0) / 10.0;
        return String.format(
                "리더 발화 비율이 %.0f%% → %.0f%%로 변하는 동안, 팀의 솔직한 발화 신호는 %.1f → %.1f(%s%.1f)로 움직였습니다.",
                rFirst, rLast, sFirst, sLast, sDiff >= 0 ? "+" : "", sDiff);
    }

    // 멤버별 마지막 1on1 경과일 — 오래된 순(관심 필요한 멤버 먼저), 미팅 없으면 최상단
    private List<LeaderGrowthResponse.MemberCadence> buildMemberCadence(Long leaderId, List<Meeting> meetings) {
        Long teamId = userRepository.findById(leaderId).map(User::getTeamId).orElse(null);
        if (teamId == null) return List.of();

        List<User> members = userRepository.findByTeamId(teamId).stream()
                .filter(u -> u.getRole() == UserRole.MEMBER)
                .toList();
        if (members.isEmpty()) return List.of();

        // meetings는 createdAt 내림차순 — 멤버별 첫 등장이 가장 최근 미팅
        Map<Long, Meeting> latestByMember = new LinkedHashMap<>();
        meetings.forEach(m -> latestByMember.putIfAbsent(m.getMemberId(), m));

        LocalDate today = LocalDate.now();
        return members.stream()
                .map(member -> {
                    Meeting last = latestByMember.get(member.getId());
                    if (last == null) {
                        return new LeaderGrowthResponse.MemberCadence(member.getId(), member.getName(), null, null);
                    }
                    LocalDateTime at = last.getScheduledAt() != null ? last.getScheduledAt() : last.getCreatedAt();
                    LocalDate date = at.toLocalDate();
                    return new LeaderGrowthResponse.MemberCadence(
                            member.getId(), member.getName(), date.toString(),
                            ChronoUnit.DAYS.between(date, today));
                })
                .sorted(Comparator.comparing(LeaderGrowthResponse.MemberCadence::daysSinceLastMeeting,
                        Comparator.nullsFirst(Comparator.reverseOrder())))
                .toList();
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
