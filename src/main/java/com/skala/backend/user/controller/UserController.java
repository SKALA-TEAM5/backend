package com.skala.backend.user.controller;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.auth.support.AuthCookieFactory;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.dto.AdminCreateUserRequest;
import com.skala.backend.user.dto.AdminUpdateUserRequest;
import com.skala.backend.user.dto.UserDetailResponse;
import com.skala.backend.user.dto.UserListResponse;
import com.skala.backend.user.dto.WithdrawRequest;
import com.skala.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "사용자 관리", description = "관리자용 사용자 조회, 생성, 수정, 삭제 API")
@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
public class UserController {

	private final UserService userService;
	private final AuthCookieFactory authCookieFactory;

	public UserController(UserService userService, AuthCookieFactory authCookieFactory) {
		this.userService = userService;
		this.authCookieFactory = authCookieFactory;
	}

	@GetMapping
	@Operation(
			summary = "사용자 목록 조회",
			description = "역할 또는 키워드로 사용자를 필터링합니다. 관리자 화면의 사용자 관리 목록에서 사용합니다."
	)
	public ResponseEntity<ApiResponse<UserListResponse>> listUsers(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(
					description = "조회할 역할 코드입니다. 생략하면 전체 역할을 조회합니다.",
					example = "user",
					schema = @Schema(allowableValues = {"system_admin", "admin", "user", "agent"})
			)
			@RequestParam(required = false) RoleCode roleCode,
			@Parameter(description = "사번 또는 이름 검색어입니다.", example = "김")
			@RequestParam(required = false) String keyword
	) {
		UserListResponse response = userService.listUsers(currentUser.id(), roleCode, keyword);
		return ResponseEntity.ok(ApiResponse.success(response, "사용자 목록 조회에 성공했습니다."));
	}

	@PostMapping
	@Operation(
			summary = "사용자 생성",
			description = "관리자가 새 사용자를 생성합니다. employeeNo는 로그인에 사용하는 사번입니다."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> createUser(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody AdminCreateUserRequest request
	) {
		UserDetailResponse response = userService.createUser(currentUser.id(), request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "사용자 생성에 성공했습니다."));
	}

	@GetMapping("/{userId}")
	@Operation(
			summary = "사용자 상세 조회",
			description = "사용자 ID로 특정 사용자의 상세 정보를 조회합니다."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> getUser(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "조회할 사용자 ID", example = "1")
			@PathVariable Long userId
	) {
		UserDetailResponse response = userService.getUser(currentUser.id(), userId);
		return ResponseEntity.ok(ApiResponse.success(response, "사용자 조회에 성공했습니다."));
	}

	@PatchMapping("/{userId}")
	@Operation(
			summary = "사용자 수정",
			description = "이름, 비밀번호, 역할 중 필요한 값만 전달해 사용자를 수정합니다."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> updateUser(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "수정할 사용자 ID", example = "1")
			@PathVariable Long userId,
			@Valid @RequestBody AdminUpdateUserRequest request
	) {
		UserDetailResponse response = userService.updateUser(currentUser.id(), userId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "사용자 수정에 성공했습니다."));
	}

	@DeleteMapping("/{userId}")
	@Operation(
			summary = "사용자 삭제",
			description = "관리자가 특정 사용자를 삭제합니다. 실제 삭제 정책은 서비스 로직을 따릅니다."
	)
	public ResponseEntity<ApiResponse<Void>> deleteUser(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Parameter(description = "삭제할 사용자 ID", example = "1")
			@PathVariable Long userId
	) {
		userService.deleteUser(currentUser.id(), userId);
		return ResponseEntity.ok(ApiResponse.success(null, "사용자 삭제에 성공했습니다."));
	}

	@GetMapping("/me")
	@Operation(
			tags = "내 계정",
			summary = "내 프로필 조회",
			description = "현재 로그인한 사용자의 프로필 정보를 조회합니다."
	)
	public ResponseEntity<ApiResponse<UserDetailResponse>> getMyProfile(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser
	) {
		UserDetailResponse response = userService.getMyProfile(currentUser.id());
		return ResponseEntity.ok(ApiResponse.success(response, "내 프로필 조회에 성공했습니다."));
	}

	@DeleteMapping("/me")
	@Operation(
			tags = "내 계정",
			summary = "회원탈퇴",
			description = "현재 로그인한 사용자의 비밀번호를 확인한 뒤 회원탈퇴를 처리하고 인증 쿠키를 만료시킵니다."
	)
	public ResponseEntity<ApiResponse<Void>> withdraw(
			@Parameter(hidden = true)
			@AuthenticationPrincipal AuthenticatedUser currentUser,
			@Valid @RequestBody WithdrawRequest request
	) {
		userService.withdraw(currentUser.id(), request.password());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, authCookieFactory.expiredAccessToken().toString())
				.header(HttpHeaders.SET_COOKIE, authCookieFactory.expiredRefreshToken().toString())
				.body(ApiResponse.success(null, "회원탈퇴가 완료되었습니다."));
	}
}
