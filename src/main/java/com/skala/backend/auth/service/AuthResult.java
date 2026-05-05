package com.skala.backend.auth.service;

import com.skala.backend.auth.dto.AuthResponse;

public record AuthResult(
		AuthResponse response,
		TokenPair tokens
) {
}
