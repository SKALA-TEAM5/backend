package com.skala.backend.auth.controller;

import com.skala.backend.auth.dto.AuthResponse;
import com.skala.backend.auth.dto.LoginRequest;
import com.skala.backend.auth.dto.SignupRequest;
import com.skala.backend.auth.service.AuthResult;
import com.skala.backend.auth.service.AuthService;
import com.skala.backend.auth.support.AuthCookieFactory;
import com.skala.backend.global.config.OpenApiConfig;
import com.skala.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "인증", description = "회원가입, 로그인, 토큰 재발급, 로그아웃 API")
public class AuthController {

	private final AuthService authService;
	private final AuthCookieFactory authCookieFactory;

	public AuthController(AuthService authService, AuthCookieFactory authCookieFactory) {
		this.authService = authService;
		this.authCookieFactory = authCookieFactory;
	}

	@PostMapping("/signup")
	@Operation(
			summary = "회원가입",
			description = "사번, 이름, 비밀번호, 역할을 입력해 새 계정을 생성합니다. 생성 직후 자동 로그인은 하지 않습니다."
	)
	public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
		AuthResponse response = authService.signup(request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "회원가입에 성공했습니다."));
	}

	@PostMapping("/login")
	@Operation(
			summary = "로그인",
			description = "사번과 비밀번호를 검증한 뒤 access_token, refresh_token을 HttpOnly 쿠키로 발급합니다."
	)
	public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
		AuthResult result = authService.login(request);
		return authResponse(result, "로그인에 성공했습니다.");
	}

	@PostMapping("/refresh")
	@Operation(
			summary = "토큰 재발급",
			description = "refresh_token 쿠키를 검증해 새로운 access_token과 refresh_token 쿠키를 발급합니다."
	)
	@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
	public ResponseEntity<ApiResponse<AuthResponse>> refresh(
			@Parameter(hidden = true)
			@CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken
	) {
		AuthResult result = authService.refresh(refreshToken);
		return authResponse(result, "토큰 재발급에 성공했습니다.");
	}

	@PostMapping("/logout")
	@Operation(
			summary = "로그아웃",
			description = "refresh_token을 무효화하고 access_token, refresh_token 쿠키를 만료시킵니다."
	)
	@SecurityRequirement(name = OpenApiConfig.COOKIE_AUTH)
	public ResponseEntity<ApiResponse<Void>> logout(
			@Parameter(hidden = true)
			@CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken
	) {
		authService.logout(refreshToken);

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, authCookieFactory.expiredAccessToken().toString())
				.header(HttpHeaders.SET_COOKIE, authCookieFactory.expiredRefreshToken().toString())
				.body(ApiResponse.success(null, "로그아웃에 성공했습니다."));
	}

	private ResponseEntity<ApiResponse<AuthResponse>> authResponse(AuthResult result, String message) {
		ResponseCookie accessTokenCookie = authCookieFactory.accessToken(result.tokens().accessToken());
		ResponseCookie refreshTokenCookie = authCookieFactory.refreshToken(result.tokens().refreshToken());

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
				.header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
				.body(ApiResponse.success(result.response(), message));
	}
}
