package com.readb.controller;

import com.readb.common.exception.BusinessException;
import com.readb.common.exception.ErrorCode;
import com.readb.common.response.ApiResponse;
import com.readb.domain.actionplan.ActionPlan;
import com.readb.repository.ActionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/action-plans")
@RequiredArgsConstructor
public class ActionPlanController {

    private final ActionPlanRepository actionPlanRepository;

    @PatchMapping("/{planId}/complete")
    public ApiResponse<Void> complete(
            @PathVariable Long planId,
            @AuthenticationPrincipal Long leaderId) {
        ActionPlan plan = actionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEETING_NOT_FOUND));
        if (!plan.getLeaderId().equals(leaderId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        plan.complete();
        actionPlanRepository.save(plan);
        return ApiResponse.ok();
    }
}
