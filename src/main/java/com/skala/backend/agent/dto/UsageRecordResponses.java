package com.skala.backend.agent.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public final class UsageRecordResponses {

	private UsageRecordResponses() {}

	@Schema(description = "사용자별 토큰 사용량 집계")
	public record ByUser(
			@Schema(description = "사용자 ID") Long userId,
			@Schema(description = "사용자 이름") String userName,
			@Schema(description = "입력 토큰 합계") long inputTokens,
			@Schema(description = "출력 토큰 합계") long outputTokens,
			@Schema(description = "총 비용 (USD)") BigDecimal costUsd,
			@Schema(description = "호출 횟수") long callCount
	) {}

	@Schema(description = "프로젝트별 토큰 사용량 집계")
	public record ByProject(
			@Schema(description = "프로젝트 ID") Long projectId,
			@Schema(description = "프로젝트명") String projectName,
			@Schema(description = "입력 토큰 합계") long inputTokens,
			@Schema(description = "출력 토큰 합계") long outputTokens,
			@Schema(description = "총 비용 (USD)") BigDecimal costUsd,
			@Schema(description = "호출 횟수") long callCount
	) {}

	@Schema(description = "에이전트별 토큰 사용량 집계")
	public record ByAgent(
			@Schema(description = "에이전트 유형", example = "safety-doc") String agentTypeCode,
			@Schema(description = "입력 토큰 합계") long inputTokens,
			@Schema(description = "출력 토큰 합계") long outputTokens,
			@Schema(description = "총 비용 (USD)") BigDecimal costUsd,
			@Schema(description = "호출 횟수") long callCount
	) {}

	@Schema(description = "월별 토큰 사용량 집계")
	public record ByMonth(
			@Schema(description = "연월 (YYYY-MM)") String month,
			@Schema(description = "입력 토큰 합계") long inputTokens,
			@Schema(description = "출력 토큰 합계") long outputTokens,
			@Schema(description = "총 비용 (USD)") BigDecimal costUsd,
			@Schema(description = "호출 횟수") long callCount
	) {}

	@Schema(description = "일별 토큰 사용량 집계")
	public record ByDate(
			@Schema(description = "날짜 (YYYY-MM-DD)") String date,
			@Schema(description = "입력 토큰 합계") long inputTokens,
			@Schema(description = "출력 토큰 합계") long outputTokens,
			@Schema(description = "총 비용 (USD)") BigDecimal costUsd,
			@Schema(description = "호출 횟수") long callCount
	) {}
}
