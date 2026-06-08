package com.skala.backend.dashboard.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.dashboard.dto.DashboardResponses;
import com.skala.backend.dashboard.service.DashboardService;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dashboard")
@Tag(name = "Dashboard", description = "관리자 대시보드 API (admin 전용)")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "프로젝트 요약", description = "전체 프로젝트 수와 보완 요청 중인 프로젝트 수를 반환합니다.")
    public ResponseEntity<ApiResponse<DashboardResponses.Summary>> getSummary(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getSummary(currentUser.id()), "프로젝트 요약 조회에 성공했습니다."));
    }

    @GetMapping("/ai-usage")
    @Operation(
            summary = "AI 사용금액 집계",
            description = """
                    전체 토큰·호출 수 합계와 사용자별 TOP5, 프로젝트별 TOP5를 반환합니다.

                    `year`/`month` 파라미터로 특정 월 필터 가능. `year`만 전달하면 해당 연도 전체.
                    둘 다 생략하면 전체 기간 집계.
                    """
    )
    public ResponseEntity<ApiResponse<DashboardResponses.AiUsage>> getAiUsage(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Parameter(description = "연도", example = "2026")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "월 (1-12)", example = "6")
            @RequestParam(required = false) Integer month
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getAiUsage(currentUser.id(), year, month),
                "AI 사용량 조회에 성공했습니다."));
    }

    @GetMapping("/supplement-progress")
    @Operation(
            summary = "담당자별 보완 진행 현황",
            description = "이번 달 기준으로 각 담당자가 맡은 프로젝트 중 보완 요청(supplement_required) 상태인 사용내역서 수를 TOP3로 반환합니다."
    )
    public ResponseEntity<ApiResponse<List<DashboardResponses.SupplementProgress>>> getSupplementProgress(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getSupplementProgress(currentUser.id()),
                "담당자별 보완 현황 조회에 성공했습니다."));
    }
}
