package com.skala.backend.auth.service;

import com.skala.backend.auth.config.AuthProperties;
import com.skala.backend.auth.domain.RefreshToken;
import com.skala.backend.auth.repository.RefreshTokenRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

	private static final int REFRESH_TOKEN_BYTE_LENGTH = 64;

	private final RefreshTokenRepository refreshTokenRepository;
	private final AuthProperties authProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, AuthProperties authProperties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.authProperties = authProperties;
	}

	@Transactional
	public String createRefreshToken(User user) {
		String rawToken = generateTokenValue();
		String tokenHash = hash(rawToken);
		Instant expiresAt = Instant.now().plus(authProperties.getJwt().getRefreshTokenValidity());

		refreshTokenRepository.save(RefreshToken.create(user, tokenHash, expiresAt));
		return rawToken;
	}

	@Transactional
	public User rotate(String rawToken) {
		RefreshToken refreshToken = findActiveToken(rawToken);
		refreshToken.revoke(Instant.now());
		return refreshToken.getUser();
	}

	@Transactional
	public void revoke(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hash(rawToken))
				.ifPresent(token -> token.revoke(Instant.now()));
	}

	@Transactional
	public void revokeActiveTokensByUserId(Long userId) {
		refreshTokenRepository.revokeActiveTokensByUserId(userId, Instant.now());
	}

	@Transactional
	public void deleteTokensByUserId(Long userId) {
		refreshTokenRepository.deleteByUserId(userId);
	}

	private RefreshToken findActiveToken(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.");
		}

		RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."));

		if (!refreshToken.isActive(Instant.now())) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");
		}
		return refreshToken;
	}

	private String generateTokenValue() {
		byte[] bytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}
}
