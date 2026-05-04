package com.skala.backend.auth.service;

import com.skala.backend.auth.dto.AuthResponse;
import com.skala.backend.auth.dto.LoginRequest;
import com.skala.backend.auth.dto.SignupRequest;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.dto.UserProfileResponse;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public AuthResponse signup(SignupRequest request) {
		if (userRepository.existsByEmployeeNo(request.employeeNo())) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 존재하는 사번입니다.");
		}

		User user = User.create(
				request.employeeNo(),
				request.realName(),
				passwordEncoder.encode(request.password()),
				request.roleCode()
		);

		try {
			User savedUser = userRepository.save(user);
			return new AuthResponse(UserProfileResponse.from(savedUser));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 존재하는 사번입니다.");
		}
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmployeeNo(request.employeeNo())
				.orElseThrow(this::invalidCredentials);

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw invalidCredentials();
		}

		return new AuthResponse(UserProfileResponse.from(user));
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "사번 또는 비밀번호가 일치하지 않습니다.");
	}
}
