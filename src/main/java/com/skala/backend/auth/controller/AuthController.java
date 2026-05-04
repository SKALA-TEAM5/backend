package com.skala.backend.auth.controller;

import com.skala.backend.auth.dto.AuthResponse;
import com.skala.backend.auth.dto.LoginRequest;
import com.skala.backend.auth.dto.SignupRequest;
import com.skala.backend.auth.service.AuthService;
import com.skala.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) { this.authService = authService; }

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
		AuthResponse response = authService.signup(request);
		return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(ApiResponse.success(response, "회원가입에 성공했습니다."));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
		AuthResponse response = authService.login(request);
		return ResponseEntity.ok(ApiResponse.success(response, "로그인에 성공했습니다."));
	}
}
