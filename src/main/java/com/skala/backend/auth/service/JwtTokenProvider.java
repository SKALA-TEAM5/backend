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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

@Component
public class JwtTokenProvider {

	private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

	private static final String ROLE_CLAIM = "role";

	/** application.yaml에 커밋된 로컬 개발용 기본 secret. 운영(prod 프로필)에서는 사용 금지. */
	static final String INSECURE_DEFAULT_SECRET = "local-development-jwt-secret-must-be-at-least-32-bytes";

	private final AuthProperties authProperties;
	private final Environment environment;
	private SecretKey key;

	public JwtTokenProvider(AuthProperties authProperties, Environment environment) {
		this.authProperties = authProperties;
		this.environment = environment;
	}

	@PostConstruct
	void init() {
		String secret = authProperties.getJwt().getSecret();
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("app.auth.jwt.secret must be at least 32 bytes.");
		}
		if (INSECURE_DEFAULT_SECRET.equals(secret)) {
			if (isProductionProfile()) {
				throw new IllegalStateException(
						"app.auth.jwt.secret is the committed default value. Set APP_JWT_SECRET to a unique secret in production.");
			}
			log.warn("app.auth.jwt.secret is the committed development default. "
					+ "This MUST be overridden via APP_JWT_SECRET outside local development.");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	private boolean isProductionProfile() {
		return Arrays.asList(environment.getActiveProfiles()).contains("prod");
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
