package com.skala.backend.auth.service;

import com.skala.backend.auth.config.AuthProperties;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

	private static final String ROLE_CLAIM = "role";

	private final AuthProperties authProperties;
	private SecretKey key;

	public JwtTokenProvider(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	@PostConstruct
	void init() {
		String secret = authProperties.getJwt().getSecret();
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("app.auth.jwt.secret must be at least 32 bytes.");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(authProperties.getJwt().getAccessTokenValidity());

		return Jwts.builder()
				.subject(user.getId().toString())
				.claim(ROLE_CLAIM, user.getRoleCode().getValue())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(key)
				.compact();
	}

	public AuthenticatedUser parseAccessToken(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			Long userId = Long.parseLong(claims.getSubject());
			RoleCode roleCode = RoleCode.from(claims.get(ROLE_CLAIM, String.class));
			return new AuthenticatedUser(userId, roleCode);
		} catch (IllegalArgumentException | JwtException exception) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");
		}
	}
}
