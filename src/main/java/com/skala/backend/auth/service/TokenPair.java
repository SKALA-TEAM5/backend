package com.skala.backend.auth.service;

public record TokenPair(
		String accessToken,
		String refreshToken
) {
}
