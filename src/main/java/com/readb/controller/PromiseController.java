package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.domain.promise.Promise;
import com.readb.dto.promise.FulfillmentRateResponse;
import com.readb.dto.promise.OverduePromiseResponse;
import com.readb.service.analysis.PromiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/promises")
@RequiredArgsConstructor
public class PromiseController {

    private final PromiseService promiseService;

    @GetMapping
    public ApiResponse<List<Promise>> getPromises(
            @RequestParam Long teamId,
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(promiseService.getPromisesByTeam(teamId, userId));
    }

    @GetMapping("/fulfillment-rate")
    public ApiResponse<FulfillmentRateResponse> getFulfillmentRate(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(promiseService.getFulfillmentRate(userId));
    }

    @GetMapping("/overdue")
    public ApiResponse<List<OverduePromiseResponse>> getOverduePromises(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long memberId) {
        return ApiResponse.ok(promiseService.getOverduePromises(userId, memberId));
    }

    @PatchMapping("/{promiseId}/complete")
    public ApiResponse<Void> completePromise(
            @PathVariable Long promiseId,
            @AuthenticationPrincipal Long userId) {
        promiseService.completePromise(promiseId, userId);
        return ApiResponse.ok(null);
    }

}
