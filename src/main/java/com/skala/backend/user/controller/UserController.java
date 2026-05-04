package com.skala.backend.user.controller;

import com.skala.backend.global.response.ApiResponse;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.dto.AdminCreateUserRequest;
import com.skala.backend.user.dto.AdminUpdateUserRequest;
import com.skala.backend.user.dto.UserDetailResponse;
import com.skala.backend.user.dto.UserListResponse;
import com.skala.backend.user.dto.WithdrawRequest;
import com.skala.backend.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<UserListResponse>> listUsers(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@RequestParam(required = false) RoleCode roleCode,
			@RequestParam(required = false) String keyword
	) {
		UserListResponse response = userService.listUsers(accessToken, roleCode, keyword);
		return ResponseEntity.ok(ApiResponse.success(response, "사용자 목록 조회에 성공했습니다."));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<UserDetailResponse>> createUser(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@Valid @RequestBody AdminCreateUserRequest request
	) {
		UserDetailResponse response = userService.createUser(accessToken, request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "사용자 생성에 성공했습니다."));
	}

	@GetMapping("/{userId}")
	public ResponseEntity<ApiResponse<UserDetailResponse>> getUser(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long userId
	) {
		UserDetailResponse response = userService.getUser(accessToken, userId);
		return ResponseEntity.ok(ApiResponse.success(response, "사용자 조회에 성공했습니다."));
	}

	@PatchMapping("/{userId}")
	public ResponseEntity<ApiResponse<UserDetailResponse>> updateUser(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long userId,
			@Valid @RequestBody AdminUpdateUserRequest request
	) {
		UserDetailResponse response = userService.updateUser(accessToken, userId, request);
		return ResponseEntity.ok(ApiResponse.success(response, "사용자 수정에 성공했습니다."));
	}

	@DeleteMapping("/{userId}")
	public ResponseEntity<ApiResponse<Void>> deleteUser(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@PathVariable Long userId
	) {
		userService.deleteUser(accessToken, userId);
		return ResponseEntity.ok(ApiResponse.success(null, "사용자 삭제에 성공했습니다."));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<UserDetailResponse>> getMyProfile(
			@CookieValue(name = "access_token", required = false) String accessToken
	) {
		UserDetailResponse response = userService.getMyProfile(accessToken);
		return ResponseEntity.ok(ApiResponse.success(response, "내 프로필 조회에 성공했습니다."));
	}

	@DeleteMapping("/me")
	public ResponseEntity<ApiResponse<Void>> withdraw(
			@CookieValue(name = "access_token", required = false) String accessToken,
			@Valid @RequestBody WithdrawRequest request
	) {
		userService.withdraw(accessToken, request.password());
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, expiredAccessTokenCookie().toString())
				.body(ApiResponse.success(null, "회원탈퇴가 완료되었습니다."));
	}

	private ResponseCookie expiredAccessTokenCookie() {
		return ResponseCookie.from("access_token", "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/")
				.maxAge(0)
				.build();
	}
}
