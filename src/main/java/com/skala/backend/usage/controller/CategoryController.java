package com.skala.backend.usage.controller;

import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.project.service.CodeLookupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/categories")
@Tag(name = "공통 코드", description = "카테고리 등 공통 코드 조회 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class CategoryController {

	private final CodeLookupService codeLookupService;

	public CategoryController(CodeLookupService codeLookupService) {
		this.codeLookupService = codeLookupService;
	}

	@GetMapping
	@Operation(summary = "카테고리 목록 조회", description = "세부항목 추가 폼 드롭다운용 카테고리 목록을 반환합니다.")
	public ResponseEntity<ApiResponse<List<CategoryItem>>> getCategories() {
		List<CategoryItem> items = codeLookupService.categoryNames().entrySet().stream()
				.sorted(java.util.Map.Entry.comparingByKey())
				.map(e -> new CategoryItem(e.getKey(), e.getValue()))
				.toList();
		return ResponseEntity.ok(ApiResponse.success(items, "카테고리 목록 조회에 성공했습니다."));
	}

	public record CategoryItem(String code, String name) {}
}
