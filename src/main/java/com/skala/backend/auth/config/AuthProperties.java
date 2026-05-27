package com.skala.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

	private final Jwt jwt = new Jwt();
	private final Cookie cookie = new Cookie();

	public Jwt getJwt() {
		return jwt;
	}

	public Cookie getCookie() {
		return cookie;
	}

	public static class Jwt {
		private String secret;
		private Duration accessTokenValidity;
		private Duration refreshTokenValidity;

		public String getSecret() {
			return secret;
		}

		public void setSecret(String secret) {
			this.secret = secret;
		}

		public Duration getAccessTokenValidity() {
			return accessTokenValidity;
		}

		public void setAccessTokenValidity(Duration accessTokenValidity) {
			this.accessTokenValidity = accessTokenValidity;
		}

		public Duration getRefreshTokenValidity() {
			return refreshTokenValidity;
		}

		public void setRefreshTokenValidity(Duration refreshTokenValidity) {
			this.refreshTokenValidity = refreshTokenValidity;
		}
	}

	public static class Cookie {
		private boolean secure;
		private String sameSite;

		public boolean isSecure() {
			return secure;
		}

		public void setSecure(boolean secure) {
			this.secure = secure;
		}

		public String getSameSite() {
			return sameSite;
		}

		public void setSameSite(String sameSite) {
			this.sameSite = sameSite;
		}
	}
}
