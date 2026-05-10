package com.skala.backend.auth.service;

import com.skala.backend.auth.dto.AuthResponse;
import com.skala.backend.auth.dto.LoginRequest;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.dto.UserProfileResponse;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenService refreshTokenService;

	public AuthService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider,
			RefreshTokenService refreshTokenService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.refreshTokenService = refreshTokenService;
	}

	@Transactional
	public AuthResult login(LoginRequest request) {
		// User의 EmployeeNo 검증
		User user = userRepository.findByEmployeeNo(request.employeeNo())
				.orElseThrow(this::invalidCredentials);

		// User의 Password 검증
		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw invalidCredentials();
		}

		return authResult(user);
	}

	@Transactional
	public AuthResult refresh(String refreshToken) {
		// User의 refresh token 발급
		User user = refreshTokenService.rotate(refreshToken);
		return authResult(user);
	}

	@Transactional
	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
	}

	private AuthResult authResult(User user) {
		return new AuthResult(
				new AuthResponse(UserProfileResponse.from(user)),
				new TokenPair(
						jwtTokenProvider.createAccessToken(user),
						refreshTokenService.createRefreshToken(user)
				)
		);
	}
}
