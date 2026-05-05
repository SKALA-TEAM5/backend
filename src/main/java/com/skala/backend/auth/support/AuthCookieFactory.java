package com.skala.backend.auth.support;

import com.skala.backend.auth.config.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

	public static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
	public static final String REFRESH_TOKEN_COOKIE_NAME = "refresh_token";

	private final AuthProperties authProperties;

	public AuthCookieFactory(AuthProperties authProperties) {
		this.authProperties = authProperties;
	}

	public ResponseCookie accessToken(String token) {
		return baseCookie(ACCESS_TOKEN_COOKIE_NAME, token)
				.maxAge(authProperties.getJwt().getAccessTokenValidity())
				.build();
	}

	public ResponseCookie refreshToken(String token) {
		return baseCookie(REFRESH_TOKEN_COOKIE_NAME, token)
				.maxAge(authProperties.getJwt().getRefreshTokenValidity())
				.build();
	}

	public ResponseCookie expiredAccessToken() {
		return expiredCookie(ACCESS_TOKEN_COOKIE_NAME);
	}

	public ResponseCookie expiredRefreshToken() {
		return expiredCookie(REFRESH_TOKEN_COOKIE_NAME);
	}

	private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value) {
		return ResponseCookie.from(name, value)
				.httpOnly(true)
				.secure(authProperties.getCookie().isSecure())
				.sameSite(authProperties.getCookie().getSameSite())
				.path("/");
	}

	private ResponseCookie expiredCookie(String name) {
		return baseCookie(name, "")
				.maxAge(0)
				.build();
	}
}
