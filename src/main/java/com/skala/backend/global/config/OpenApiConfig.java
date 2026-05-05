package com.skala.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	public static final String COOKIE_AUTH = "CookieAuth";

	@Bean
	OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("SKALA 안전 관리 API")
						.description("프론트엔드 연동을 위한 백엔드 API 문서입니다. 응답은 기본적으로 success, data, message 형태를 사용합니다.")
						.version("v1"))
				.components(new Components()
						.addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE)
								.name("access_token")
								.description("로그인 또는 토큰 재발급 후 발급되는 HttpOnly access_token 쿠키입니다.")));
	}
}
