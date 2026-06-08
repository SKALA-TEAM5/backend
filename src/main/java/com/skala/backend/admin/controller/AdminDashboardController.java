package com.skala.backend.admin.controller;

import com.skala.backend.admin.dto.AdminDashboardResponses.DashboardResponse;
import com.skala.backend.admin.dto.AdminDashboardResponses.ProjectListResponse;
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
@RequestMapping("/admin/dashboard")
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

    @GetMapping("/projects")
    @Operation(
            summary = "프로젝트 현황 리스트 조회",
            description = """
                    이름/계약번호/담당자/상태로 검색하고 각 컬럼으로 정렬할 수 있습니다.

                    **sortBy**: projectName, contractNo, progressRate, usageRate, startDate, endDate, assignees
                    **sortDir**: asc, desc
                    """)
    public ResponseEntity<ApiResponse<ProjectListResponse>> getProjects(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String assigneeName,
            @RequestParam(required = false) String statusCode,
            @RequestParam(defaultValue = "projectName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getProjectList(currentUser.id(), keyword, assigneeName, statusCode, sortBy, sortDir, page, size),
                "프로젝트 목록 조회에 성공했습니다."));
    }
}
