package com.skala.backend.legal.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.legal.dto.LawChangeResponses.RecentResponse;
import com.skala.backend.legal.service.LawChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/law-changes")
@Tag(name = "법령 변경 이력", description = "법령 개정 감지 이력 조회 API (system_admin, admin 전용)")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class LawChangeController {

    private final LawChangeService service;

    public LawChangeController(LawChangeService service) {
        this.service = service;
    }

    @GetMapping("/recent")
    @Operation(
            summary = "최근 법령 변경 이력 조회",
            description = """
                    가장 최근 배치 실행(run_id 기준)에서 감지된 법령 변경 목록을 반환합니다.

                    - 변경이 없으면 `hasChanges: false`, `changedLaws: []` 반환
                    - `system_admin`, `admin` role만 접근 가능
                    - 프론트엔드 로그인 후 팝업 표시 용도
                    """)
    public ResponseEntity<ApiResponse<RecentResponse>> getRecent(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getRecent(currentUser),
                "법령 변경 이력 조회에 성공했습니다."));
    }
}
