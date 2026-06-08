package com.skala.backend.admin.controller;

import com.skala.backend.admin.dto.AdminDashboardResponses.AiUsageSummary;
import com.skala.backend.admin.dto.AdminDashboardResponses.DashboardResponse;
import com.skala.backend.admin.service.AdminDashboardService;
import com.skala.backend.auth.security.AuthenticatedUser;
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

@RestController
@RequestMapping("/dashboard")
@Tag(name = "관리자 대시보드", description = "admin 전용 통계 및 현황 조회 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "대시보드 요약 조회",
            description = "전체/검토필요 프로젝트 건수, AI 사용금액 통계, 담당자별 보완 현황을 반환합니다.")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getDashboard(currentUser.id()), "대시보드 조회에 성공했습니다."));
    }

    @GetMapping("/ai-usage")
    @Operation(
            summary = "AI 사용금액 집계",
            description = """
                    전체 토큰·호출 수 합계와 에이전트별, 사용자별 TOP8, 프로젝트별 TOP8을 반환합니다.

                    `year`/`month` 파라미터로 특정 월 필터 가능. `year`만 전달하면 해당 연도 전체.
                    둘 다 생략하면 전체 기간 집계.
                    """)
    public ResponseEntity<ApiResponse<AiUsageSummary>> getAiUsage(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Parameter(description = "연도", example = "2026") @RequestParam(required = false) Integer year,
            @Parameter(description = "월 (1-12)", example = "6") @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getAiUsage(currentUser.id(), year, month),
                "AI 사용량 조회에 성공했습니다."));
    }
}
