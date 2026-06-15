package com.skala.backend.agent.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.domain.AgentTypeCode;
import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.file.service.ProjectFileService;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


@Service
public class AgentService {

	private static final Logger log = LoggerFactory.getLogger(AgentService.class);

	private final FastApiAgentClient fastApiAgentClient;
	private final ProjectAccessService projectAccessService;
	private final AgentLogRepository agentLogRepository;
	private final AgentAsyncService agentAsyncService;
	private final ProjectFileService projectFileService;
	private final int validateStaleSeconds;
	private final int legalStaleSeconds;
	private final int reportStaleSeconds;

	public AgentService(FastApiAgentClient fastApiAgentClient, ProjectAccessService projectAccessService,
			AgentLogRepository agentLogRepository, AgentAsyncService agentAsyncService,
			ProjectFileService projectFileService,
			@Value("${app.fastapi.stale-threshold.validate-seconds:900}") int validateStaleSeconds,
			@Value("${app.fastapi.stale-threshold.legal-seconds:900}")    int legalStaleSeconds,
			@Value("${app.fastapi.stale-threshold.report-seconds:900}")   int reportStaleSeconds) {
		this.fastApiAgentClient = fastApiAgentClient;
		this.projectAccessService = projectAccessService;
		this.agentLogRepository = agentLogRepository;
		this.agentAsyncService = agentAsyncService;
		this.projectFileService = projectFileService;
		this.validateStaleSeconds = validateStaleSeconds;
		this.legalStaleSeconds = legalStaleSeconds;
		this.reportStaleSeconds = reportStaleSeconds;
	}

	public AgentResponses.ParseResult parse(Long currentUserId, Long projectId, AgentRequests.ParseRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		try {
			// 사용자가 입력한 연/월을 함께 전달한다.
			// FastAPI는 OCR로 인식한 값과 비교해 불일치 시 4xx(409)로 거부하며,
			// 그 경우 아래 catch에서 업로드 파일을 정리하고 재업로드를 유도한다.
			return fastApiAgentClient.parseUsageStatement(projectId, request.fileId(), request.year(), request.month());
		} catch (RuntimeException e) {
			// 파싱 실패 시 직전에 업로드된 사용내역서 파일은 쓸모가 없으므로 files 테이블+MinIO에서 정리한다.
			// (업로드와 parse는 별도 요청이라 parse가 실패하면 파일이 고아로 남기 때문)
			// 정리 과정에서 또 오류가 나도 원래 파싱 예외를 그대로 전파한다.
			try {
				projectFileService.delete(currentUserId, projectId, request.fileId());
			} catch (RuntimeException cleanupError) {
				log.warn("parse 실패 후 업로드 파일 정리 실패: projectId={}, fileId={}", projectId, request.fileId(), cleanupError);
			}
			throw e;
		}
	}

	public void validate(Long currentUserId, Long projectId, AgentRequests.ValidateRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		Long sid = request.usageStatementId();
		if (!agentLogRepository.existsStatementLogWithExactResultCode(sid, AgentTypeCode.CLASSI.getCode(), "success")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "parse를 먼저 실행해야 합니다.");
		}
		if (agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.SAFETY_DOC.getCode(), validateStaleSeconds)
				|| agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.LINK.getCode(), validateStaleSeconds)
				|| agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.VISION.getCode(), validateStaleSeconds)) {
			throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
		}
		// 비동기 디스패치 전 'running' 선기록으로 폴링 race를 차단한다.
		// safety-doc은 항상 실행되며(validateRunning은 OR 조건) 단독으로 validate를 running 처리한다.
		// link/vision은 증빙 유무에 따라 FastAPI가 조건부로만 실행하므로 선기록하지 않는다
		// (선기록 시 FastAPI가 갱신하지 않아 'running'에 갇혀 legal이 영구 차단되는 회귀 발생).
		agentLogRepository.upsertStatementLogRunning(projectId, sid, AgentTypeCode.SAFETY_DOC.getCode());
		agentAsyncService.fireValidate(projectId, sid, currentUserId);
	}

	public void legal(Long currentUserId, Long projectId, AgentRequests.LegalRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		Long sid = request.usageStatementId();
		if (!agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.SAFETY_DOC.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "validate를 먼저 실행해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.LINK)
				&& !agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LINK.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "link 검증을 통과해야 합니다.");
		}
		if (agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.VISION)
				&& !agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.VISION.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "vision 검증을 통과해야 합니다.");
		}
		if (agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.LEGAL.getCode(), legalStaleSeconds)) {
			throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
		}
		// 비동기 디스패치 전 'running' 선기록 — 첫 폴링이 미실행을 완료로 오인하는 race 차단.
		agentLogRepository.upsertStatementLogRunning(projectId, sid, AgentTypeCode.LEGAL.getCode());
		agentAsyncService.fireLegal(projectId, sid, currentUserId);
	}

	public void report(Long currentUserId, Long projectId, AgentRequests.ReportRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		Long sid = request.usageStatementId();
		if (!agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LEGAL.getCode())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "legal을 먼저 실행해야 합니다.");
		}
		if (agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.REPORT.getCode(), reportStaleSeconds)) {
			throw new ApiException(HttpStatus.CONFLICT, "현재 실행 중입니다.");
		}
		// 비동기 디스패치 전 'running' 선기록 — 첫 폴링이 미실행을 완료로 오인하는 race 차단.
		agentLogRepository.upsertStatementLogRunning(projectId, sid, AgentTypeCode.REPORT.getCode());
		agentAsyncService.fireReport(projectId, sid, currentUserId);
	}

	public AgentResponses.ButtonStatesResponse getButtonStates(Long currentUserId, Long projectId, Long usageStatementId) {
		projectAccessService.requireReadable(currentUserId, projectId);

		Long sid = usageStatementId;

		// validate
		boolean classiPassed = agentLogRepository.existsStatementLogWithExactResultCode(
				sid, AgentTypeCode.CLASSI.getCode(), "success");
		boolean validateRunning =
				agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.SAFETY_DOC.getCode(), validateStaleSeconds)
				|| agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.LINK.getCode(), validateStaleSeconds)
				|| agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.VISION.getCode(), validateStaleSeconds);

		AgentResponses.ButtonState validateState;
		if (validateRunning) {
			validateState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!classiPassed) {
			validateState = new AgentResponses.ButtonState(false, "parse를 먼저 실행해야 합니다.");
		} else {
			validateState = new AgentResponses.ButtonState(true, null);
		}

		// legal
		boolean legalRunning =
				agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.LEGAL.getCode(), legalStaleSeconds);
		boolean safetyDocPassed = agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.SAFETY_DOC.getCode());
		boolean linkExists = agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.LINK);
		boolean linkPassed = !linkExists || agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LINK.getCode());
		boolean visionExists = agentLogRepository.existsByUsageStatementIdAndAgentTypeCodeAndUsageStatementItemIdIsNull(sid, AgentTypeCode.VISION);
		boolean visionPassed = !visionExists || agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.VISION.getCode());

		AgentResponses.ButtonState legalState;
		if (legalRunning) {
			legalState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!safetyDocPassed) {
			legalState = new AgentResponses.ButtonState(false, "validate를 먼저 실행해야 합니다.");
		} else if (!linkPassed || !visionPassed) {
			legalState = new AgentResponses.ButtonState(false, "link 또는 vision 검증을 통과해야 합니다.");
		} else {
			legalState = new AgentResponses.ButtonState(true, null);
		}

		// report
		boolean reportRunning =
				agentLogRepository.existsActiveNonStaleLog(sid, AgentTypeCode.REPORT.getCode(), reportStaleSeconds);
		boolean legalPassed = agentLogRepository.existsStatementLogWithSuccessOrHil(sid, AgentTypeCode.LEGAL.getCode());

		AgentResponses.ButtonState reportState;
		if (reportRunning) {
			reportState = new AgentResponses.ButtonState(false, "현재 실행 중입니다.");
		} else if (!legalPassed) {
			reportState = new AgentResponses.ButtonState(false, "legal을 먼저 실행해야 합니다.");
		} else {
			reportState = new AgentResponses.ButtonState(true, null);
		}

		return new AgentResponses.ButtonStatesResponse(validateState, legalState, reportState);
	}
}
