package com.readb.controller;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.common.response.ApiResponse;
import com.readb.domain.actionplan.ActionPlan;
import com.readb.domain.meeting.Meeting;
import com.readb.repository.ActionPlanRepository;
import com.readb.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/action-plans")
@RequiredArgsConstructor
public class ActionPlanController {

    private final ActionPlanRepository actionPlanRepository;
    private final MeetingRepository meetingRepository;

    @GetMapping
    public ApiResponse<List<ActionPlan>> getActionPlans(
            @RequestParam Long meetingId,
            @AuthenticationPrincipal Long userId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!java.util.Objects.equals(meeting.getLeaderId(), userId) && !java.util.Objects.equals(meeting.getMemberId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ApiResponse.ok(actionPlanRepository.findByMeetingIdOrderByIdAsc(meetingId));
    }

    @PatchMapping("/{planId}/complete")
    public ApiResponse<Void> complete(
            @PathVariable Long planId,
            @AuthenticationPrincipal Long leaderId) {
        ActionPlan plan = actionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_PLAN_NOT_FOUND));
        if (!plan.getLeaderId().equals(leaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        plan.complete();
        actionPlanRepository.save(plan);
        return ApiResponse.ok();
    }

    @PatchMapping("/{planId}/incomplete")
    public ApiResponse<Void> incomplete(
            @PathVariable Long planId,
            @AuthenticationPrincipal Long leaderId) {
        ActionPlan plan = actionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACTION_PLAN_NOT_FOUND));
        if (!java.util.Objects.equals(plan.getLeaderId(), leaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        plan.incomplete();
        actionPlanRepository.save(plan);
        return ApiResponse.ok();
    }
}
