package com.skala.backend.global.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 성공 응답")
public record ApiResponse<T>(
		@Schema(description = "요청 성공 여부", example = "true")
		boolean success,
		@Schema(description = "응답 데이터. 데이터가 없는 API는 null입니다.")
		T data,
		@Schema(description = "사용자에게 표시 가능한 응답 메시지", example = "요청에 성공했습니다.")
		String message
) {

	public static <T> ApiResponse<T> success(T data, String message) {
		return new ApiResponse<>(true, data, message);
	}
}
