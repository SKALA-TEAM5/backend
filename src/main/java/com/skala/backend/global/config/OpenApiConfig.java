package com.skala.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

	public static final String COOKIE_AUTH = "CookieAuth";

	private static final List<String> TAG_ORDER = List.of(
			"인증",
			"내 계정",
			"사용자 관리",
			"프로젝트",
			"프로젝트 담당자",
			"프로젝트 사용내역서",
			"프로젝트 파일",
			"프로젝트 증빙 연결",
			"프로젝트 아카이브",
			"에이전트 경고",
			"Agent"
	);

	private static final List<String> PATH_ORDER = List.of(
			"/auth/login",
			"/auth/refresh",
			"/auth/logout",
			"/users/me",
			"/users",
			"/users/{userId}",
			"/projects",
			"/projects/{projectId}",
			"/projects/{projectId}/assignees",
			"/projects/{projectId}/assignees/{userId}",
			"/projects/{projectId}/usage-statements/latest",
			"/projects/{projectId}/usage-statements",
			"/projects/{projectId}/usage-statements/by-month",
			"/projects/{projectId}/usage-statements/{usageStatementId}",
			"/projects/{projectId}/files",
			"/projects/{projectId}/files/{fileId}/download",
			"/projects/{projectId}/files/{fileId}/preview",
			"/projects/{projectId}/files/{fileId}",
			"/projects/{projectId}/usage-statement-items/{itemId}/evidence-files",
			"/projects/{projectId}/evidence-file-links/{linkId}",
			"/projects/{projectId}/archive/categories",
			"/projects/{projectId}/archive/categories/{categoryCode}/items",
			"/projects/{projectId}/archive/mark-checked",
			"/projects/{projectId}/agents/warnings",
			"/projects/{projectId}/usage-statement-items/{itemId}/evidence-requirements",
			"/projects/{projectId}/agents/logs",
			"/projects/{projectId}/agents/{agentType}/run",
			"/projects/{projectId}/agents/ocr/usage-statements/parse",
			"/projects/{projectId}/agents/ocr/evidence/parse-and-match"
	);

	@Bean
	OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("SKALA 안전 관리 API")
						.description("프론트엔드 연동을 위한 백엔드 API 문서입니다. 응답은 기본적으로 success, data, message 형태를 사용합니다.")
						.version("v1"))
				.tags(List.of(
						new Tag().name("인증").description("로그인, 토큰 재발급, 로그아웃 API"),
						new Tag().name("내 계정").description("로그인한 사용자의 프로필 조회 API"),
						new Tag().name("사용자 관리").description("관리자용 사용자 조회, 생성, 수정, 삭제 API"),
						new Tag().name("프로젝트").description("프로젝트 목록, 상세, 생성, 수정, 삭제 API"),
						new Tag().name("프로젝트 담당자").description("프로젝트 담당자 조회, 배정, 해제 API"),
						new Tag().name("프로젝트 사용내역서").description("프로젝트 상세 페이지 사용내역서 탭 API"),
						new Tag().name("프로젝트 파일").description("프로젝트 증빙 파일 조회, 업로드, 다운로드 API"),
						new Tag().name("프로젝트 증빙 연결").description("사용내역서 상세항목과 증빙 파일 연결 API"),
						new Tag().name("프로젝트 아카이브").description("프로젝트 아카이브 조회 API"),
						new Tag().name("에이전트 경고").description("에이전트가 발견한 문제 항목 조회 API — 경고 목록과 서류 충족 현황을 함께 사용합니다"),
						new Tag().name("Agent").description("agent_logs 조회(R-28) 및 FastAPI agent 호출 스켈레톤")
				))
				.components(new Components()
						.addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
								.type(SecurityScheme.Type.APIKEY)
								.in(SecurityScheme.In.COOKIE)
								.name("access_token")
								.description("로그인 또는 토큰 재발급 후 발급되는 HttpOnly access_token 쿠키입니다.")));
	}

	@Bean
	OpenApiCustomizer swaggerDisplayOrderCustomizer() {
		return openApi -> {
			if (openApi.getTags() != null) {
				openApi.setTags(openApi.getTags().stream()
						.sorted(Comparator.comparingInt(tag -> orderOf(TAG_ORDER, tag.getName())))
						.toList());
			}

			if (openApi.getPaths() == null) {
				return;
			}

			Map<String, PathItem> sortedPaths = openApi.getPaths()
					.entrySet()
					.stream()
					.sorted(Comparator.comparingInt(entry -> orderOf(PATH_ORDER, entry.getKey())))
					.collect(
							LinkedHashMap::new,
							(map, entry) -> map.put(entry.getKey(), entry.getValue()),
							LinkedHashMap::putAll
					);

			Paths paths = new Paths();
			sortedPaths.forEach(paths::addPathItem);
			openApi.setPaths(paths);
		};
	}

	private static int orderOf(List<String> order, String value) {
		int index = order.indexOf(value);
		return index >= 0 ? index : order.size();
	}
}
