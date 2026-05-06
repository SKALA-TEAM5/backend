package com.skala.backend.user.service;

import com.skala.backend.global.error.ApiException;
import com.skala.backend.auth.service.RefreshTokenService;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.dto.AdminCreateUserRequest;
import com.skala.backend.user.dto.AdminUpdateUserRequest;
import com.skala.backend.user.dto.UserDetailResponse;
import com.skala.backend.user.dto.UserListResponse;
import com.skala.backend.user.dto.UserProfileResponse;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;

	public UserService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			RefreshTokenService refreshTokenService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
	}

	@Transactional(readOnly = true)
	public UserListResponse listUsers(Long currentUserId, RoleCode roleCode, String keyword) {
		requireSystemAdminOrProjectAdmin(currentUserId);
		String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

		return new UserListResponse(
				userRepository.search(roleCode, normalizedKeyword)
						.stream()
						.map(UserProfileResponse::from)
						.toList()
		);
	}

	@Transactional
	public UserDetailResponse createUser(Long currentUserId, AdminCreateUserRequest request) {
		requireSystemAdmin(currentUserId);

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
			return toDetailResponse(savedUser);
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "이미 존재하는 사번입니다.");
		}
	}

	@Transactional(readOnly = true)
	public UserDetailResponse getUser(Long currentUserId, Long userId) {
		requireSystemAdminOrProjectAdmin(currentUserId);
		return toDetailResponse(findUser(userId));
	}

	@Transactional
	public UserDetailResponse updateUser(Long currentUserId, Long userId, AdminUpdateUserRequest request) {
		requireSystemAdmin(currentUserId);

		if (request.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "수정할 값이 없습니다.");
		}

		User user = findUser(userId);

		if (request.realName() != null) {
			if (!StringUtils.hasText(request.realName())) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "realName: 공백일 수 없습니다.");
			}
			user.updateRealName(request.realName());
		}
		if (request.password() != null) {
			user.changePassword(passwordEncoder.encode(request.password()));
			refreshTokenService.revokeActiveTokensByUserId(user.getId());
		}
		if (request.roleCode() != null) {
			user.changeRole(request.roleCode());
		}

		return toDetailResponse(user);
	}

	@Transactional
	public void deleteUser(Long currentUserId, Long userId) {
		requireSystemAdmin(currentUserId);

		User user = findUser(userId);
		try {
			refreshTokenService.deleteTokensByUserId(user.getId());
			userRepository.delete(user);
			userRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "연결된 데이터가 있어 사용자를 삭제할 수 없습니다.");
		}
	}

	@Transactional(readOnly = true)
	public UserDetailResponse getMyProfile(Long currentUserId) {
		return toDetailResponse(requireCurrentUser(currentUserId));
	}

	@Transactional
	public void withdraw(Long currentUserId, String password) {
		User user = requireCurrentUser(currentUserId);
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "비밀번호가 올바르지 않습니다.");
		}

		try {
			refreshTokenService.deleteTokensByUserId(user.getId());
			userRepository.delete(user);
			userRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "연결된 데이터가 있어 사용자를 삭제할 수 없습니다.");
		}
	}

	private User requireSystemAdmin(Long currentUserId) {
		User user = requireCurrentUser(currentUserId);

		if (user.getRoleCode() != RoleCode.SYSTEM_ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return user;
	}

	private User requireSystemAdminOrProjectAdmin(Long currentUserId) {
		User user = requireCurrentUser(currentUserId);

		if (user.getRoleCode() != RoleCode.SYSTEM_ADMIN && user.getRoleCode() != RoleCode.ADMIN) {
			throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
		}
		return user;
	}

	private User requireCurrentUser(Long currentUserId) {
		if (currentUserId == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}

		return userRepository.findById(currentUserId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}

	private UserDetailResponse toDetailResponse(User user) {
		return new UserDetailResponse(UserProfileResponse.from(user));
	}
}
