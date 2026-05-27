package com.skala.backend.user.service;

import com.skala.backend.auth.service.RefreshTokenService;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import com.skala.backend.user.dto.UserRequests;
import com.skala.backend.user.dto.UserResponses;
import com.skala.backend.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

	private final ProjectAccessService projectAccessService;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;

	public UserService(
			ProjectAccessService projectAccessService,
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			RefreshTokenService refreshTokenService
	) {
		this.projectAccessService = projectAccessService;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
	}

	@Transactional(readOnly = true)
	public UserResponses.ListResponse listUsers(Long currentUserId, RoleCode roleCode, String keyword) {
		projectAccessService.requireSystemAdminOrAdmin(currentUserId);
		String keywordPattern = containsPattern(keyword);

		return new UserResponses.ListResponse(
				userRepository.search(roleCode, keywordPattern)
						.stream()
						.map(UserResponses.ProfileResponse::from)
						.toList()
		);
	}

	@Transactional
	public UserResponses.DetailResponse createUser(Long currentUserId, UserRequests.AdminCreateRequest request) {
		projectAccessService.requireSystemAdmin(currentUserId);

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
	public UserResponses.DetailResponse getUser(Long currentUserId, Long userId) {
		projectAccessService.requireSystemAdminOrAdmin(currentUserId);
		return toDetailResponse(findUser(userId));
	}

	@Transactional
	public UserResponses.DetailResponse updateUser(Long currentUserId, Long userId, UserRequests.AdminUpdateRequest request) {
		projectAccessService.requireSystemAdmin(currentUserId);

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
		projectAccessService.requireSystemAdmin(currentUserId);

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
	public UserResponses.DetailResponse getMyProfile(Long currentUserId) {
		return toDetailResponse(projectAccessService.requireCurrentUser(currentUserId));
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
	}

	private UserResponses.DetailResponse toDetailResponse(User user) {
		return new UserResponses.DetailResponse(UserResponses.ProfileResponse.from(user));
	}

	private String containsPattern(String value) {
		return StringUtils.hasText(value) ? "%" + value.trim().toLowerCase() + "%" : null;
	}
}
