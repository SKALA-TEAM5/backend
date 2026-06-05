package com.skala.backend.usage.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.usage.dto.UsageStatementItemRequests.ChangeCategoryRequest;
import com.skala.backend.usage.dto.UsageStatementItemRequests.CreateItemRequest;
import com.skala.backend.usage.dto.UsageStatementItemRequests.UpdateItemRequest;
import com.skala.backend.usage.dto.UsageStatementResponses.CreateItemResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementItemResponse;
import com.skala.backend.usage.service.UsageStatementItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects/{projectId}/usage-statements/{usageStatementId}/items")
@Tag(name = "세부항목 CRUD", description = "사용내역서 세부항목 수동 추가·수정·삭제·카테고리 이동")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class ProjectUsageStatementItemController {

	private final UsageStatementItemService itemService;

	public ProjectUsageStatementItemController(UsageStatementItemService itemService) {
		this.itemService = itemService;
	}

	@PostMapping
	@Operation(
			tags = {"AI 실행"},
			summary = "세부항목 추가 (classi)",
			description = """
					사용자가 입력한 세부항목 데이터를 FastAPI classi agent에 전달합니다.
					DB 적재는 FastAPI가 담당하며, Spring은 classi 결과만 반환합니다.

					**응답 필드**
					- `categoryChanged` : classi가 카테고리를 변경했으면 `true`
					- `changes[]` : 변경된 항목 목록. 변경 없으면 빈 배열.
					  - `itemName` / `fromCategoryCode` / `fromCategoryName` / `toCategoryCode` / `toCategoryName`

					추가된 항목은 사용내역서 재조회(`GET /usage-statements/{id}`)로 확인합니다.
					"""
	)
	public ResponseEntity<ApiResponse<CreateItemResponse>> createItem(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId,
			@Valid @RequestBody CreateItemRequest request
	) {
		CreateItemResponse response = itemService.createItem(currentUser.id(), projectId, usageStatementId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response, "세부항목이 추가되었습니다."));
	}

	@PatchMapping("/{itemId}")
	@Operation(summary = "세부항목 수정 (R-13)")
	public ResponseEntity<ApiResponse<UsageStatementItemResponse>> updateItem(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId,
			@PathVariable Long itemId,
			@Valid @RequestBody UpdateItemRequest request
	) {
		UsageStatementItemResponse response = itemService.updateItem(currentUser.id(), projectId, itemId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "세부항목이 수정되었습니다."));
	}

	@DeleteMapping("/{itemId}")
	@Operation(summary = "세부항목 삭제 (R-14)")
	public ResponseEntity<ApiResponse<Void>> deleteItem(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId,
			@PathVariable Long itemId
	) {
		itemService.deleteItem(currentUser.id(), projectId, itemId);
		return ResponseEntity.ok(ApiResponse.success(null, "세부항목이 삭제되었습니다."));
	}

	@PatchMapping("/{itemId}/category")
	@Operation(summary = "세부항목 카테고리 이동 (R-15)")
	public ResponseEntity<ApiResponse<UsageStatementItemResponse>> changeCategory(
			@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser currentUser,
			@PathVariable Long projectId,
			@PathVariable Long usageStatementId,
			@PathVariable Long itemId,
			@Valid @RequestBody ChangeCategoryRequest request
	) {
		UsageStatementItemResponse response = itemService.changeCategory(currentUser.id(), projectId, itemId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "세부항목 카테고리가 변경되었습니다."));
	}
}
