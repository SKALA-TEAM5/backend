package com.skala.backend.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.auth.service.JwtTokenProvider;
import com.skala.backend.auth.support.AuthCookieFactory;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.global.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenProvider jwtTokenProvider;
	private final ObjectMapper objectMapper;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return "OPTIONS".equalsIgnoreCase(request.getMethod())
				|| path.equals("/auth/signup")
				|| path.equals("/auth/login")
				|| path.equals("/auth/refresh")
				|| path.equals("/auth/logout")
				|| path.startsWith("/v3/api-docs")
				|| path.startsWith("/swagger-ui");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String token = findCookieValue(request, AuthCookieFactory.ACCESS_TOKEN_COOKIE_NAME);
		if (token == null || token.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		try {
			AuthenticatedUser principal = jwtTokenProvider.parseAccessToken(token);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					principal,
					null,
					List.of(new SimpleGrantedAuthority("ROLE_" + principal.roleCode().name()))
			);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
		} catch (ApiException exception) {
			SecurityContextHolder.clearContext();
			writeError(response, exception.getStatus(), exception.getMessage());
		}
	}

	private String findCookieValue(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}

		for (Cookie cookie : cookies) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ErrorResponse.of(message));
	}
}
