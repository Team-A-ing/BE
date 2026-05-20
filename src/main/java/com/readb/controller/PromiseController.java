package com.readb.controller;

import com.readb.common.response.ApiResponse;
import com.readb.domain.promise.Promise;
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
            @AuthenticationPrincipal Long leaderId) {
        // TODO: teamId 기반 필터링으로 교체 (현재 leaderId 기반 stub)
        return ApiResponse.ok(promiseService.getPromisesByLeader(leaderId));
    }
}
