package com.skala.backend.usage.service;

import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.evidence.service.EvidenceQueryService;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.CodeLookupService;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.domain.UsageStatementSummary;
import com.skala.backend.usage.dto.UsageStatementResponses.LatestUsageStatementResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementStatusResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.SourceFileResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementDetailDataResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementDetailResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementItemResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementListItemResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementListResponse;
import com.skala.backend.usage.dto.UsageStatementResponses.UsageStatementSummaryResponse;
import com.skala.backend.usage.repository.UsageStatementItemRepository;
import com.skala.backend.usage.repository.UsageStatementRepository;
import com.skala.backend.usage.repository.UsageStatementSummaryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Service
public class UsageStatementService {

	private final ProjectAccessService projectAccessService;
	private final UsageStatementRepository statementRepository;
	private final UsageStatementSummaryRepository summaryRepository;
	private final UsageStatementItemRepository itemRepository;
	private final ProjectFileRepository fileRepository;
	private final EvidenceQueryService evidenceQueryService;
	private final CodeLookupService codeLookupService;
	private final AgentLogRepository agentLogRepository;

	public UsageStatementService(
			ProjectAccessService projectAccessService,
			UsageStatementRepository statementRepository,
			UsageStatementSummaryRepository summaryRepository,
			UsageStatementItemRepository itemRepository,
			ProjectFileRepository fileRepository,
			EvidenceQueryService evidenceQueryService,
			CodeLookupService codeLookupService,
			AgentLogRepository agentLogRepository
	) {
		this.projectAccessService = projectAccessService;
		this.statementRepository = statementRepository;
		this.summaryRepository = summaryRepository;
		this.itemRepository = itemRepository;
		this.fileRepository = fileRepository;
		this.evidenceQueryService = evidenceQueryService;
		this.codeLookupService = codeLookupService;
		this.agentLogRepository = agentLogRepository;
	}

	@Transactional(readOnly = true)
	public LatestUsageStatementResponse getLatest(Long currentUserId, Long projectId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		UsageStatementDetailResponse statement = statementRepository.findFirstByProjectIdOrderByReportMonthDescRevisionNoDesc(projectId)
				.map(this::toDetail)
				.orElse(null);
		return new LatestUsageStatementResponse(projectId, statement);
	}

	@Transactional(readOnly = true)
	public UsageStatementListResponse list(Long currentUserId, Long projectId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		List<UsageStatement> statements = statementRepository.findByProjectIdOrderByReportMonthDescRevisionNoDesc(projectId);
		List<Long> statementIds = statements.stream().map(UsageStatement::getId).toList();
		Map<Long, Long> linkedCounts = codeLookupService.linkedFileCountsByStatement(statementIds);
		Map<Long, Long> unsatisfiedCounts = codeLookupService.unsatisfiedRequirementCountsByStatement(statementIds);

		List<UsageStatementListItemResponse> items = statements.stream()
				.map(statement -> new UsageStatementListItemResponse(
						statement.getId(),
						statement.getReportMonth(),
						statement.getRevisionNo(),
						statement.getDocumentWrittenDate(),
						statement.getCumulativeProgressRate(),
						statement.getStatusCode(),
						summaryRepository.countByUsageStatementId(statement.getId()),
						itemRepository.countByUsageStatementId(statement.getId()),
						linkedCounts.getOrDefault(statement.getId(), 0L),
						unsatisfiedCounts.getOrDefault(statement.getId(), 0L)
				))
				.toList();
		return new UsageStatementListResponse(projectId, items);
	}

	@Transactional(readOnly = true)
	public UsageStatementDetailDataResponse getDetail(Long currentUserId, Long projectId, Long usageStatementId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		UsageStatement statement = statementRepository.findById(usageStatementId)
				.filter(found -> found.getProjectId().equals(projectId))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
		return new UsageStatementDetailDataResponse(projectId, toDetail(statement));
	}

	@Transactional(readOnly = true)
	public UsageStatementDetailDataResponse getByMonth(Long currentUserId, Long projectId, int year, int month) {
		projectAccessService.requireReadable(currentUserId, projectId);
		LocalDate reportMonth = toReportMonth(year, month);
		UsageStatement statement = statementRepository.findFirstByProjectIdAndReportMonthOrderByRevisionNoDesc(projectId, reportMonth)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
		return new UsageStatementDetailDataResponse(projectId, toDetail(statement));
	}

	private UsageStatementDetailResponse toDetail(UsageStatement statement) {
		Map<String, String> categoryNames = codeLookupService.categoryNames();
		List<UsageStatementSummaryResponse> summaries = summaryRepository.findByUsageStatementIdOrderByCategoryCodeAsc(statement.getId())
				.stream()
				.map(summary -> toSummaryResponse(summary, categoryNames))
				.toList();
		List<UsageStatementItem> items = itemRepository.findByUsageStatementIdOrderByPageNoAscUsedOnAscIdAsc(statement.getId());
		List<Long> itemIds = items.stream().map(UsageStatementItem::getId).toList();
		Map<Long, List<com.skala.backend.usage.dto.UsageStatementResponses.EvidenceFileResponse>> filesByItemId =
				evidenceQueryService.evidenceFilesByItemIds(itemIds);
		Map<Long, List<com.skala.backend.usage.dto.UsageStatementResponses.RequirementResponse>> requirementsByItemId =
				evidenceQueryService.requirementsByItemIds(itemIds);

		List<UsageStatementItemResponse> itemResponses = items.stream()
				.map(item -> new UsageStatementItemResponse(
						item.getId(),
						item.getCategoryCode(),
						categoryNames.getOrDefault(item.getCategoryCode(), item.getCategoryCode()),
						item.getUsedOn(),
						item.getItemName(),
						item.getUnit(),
						item.getQuantity(),
						item.getUnitPrice(),
						item.getTotalAmount(),
						item.getRemark(),
						item.getPageNo(),
						filesByItemId.getOrDefault(item.getId(), List.of()),
						requirementsByItemId.getOrDefault(item.getId(), List.of())
				))
				.toList();

		return new UsageStatementDetailResponse(
				statement.getId(),
				statement.getReportMonth(),
				statement.getRevisionNo(),
				statement.getDocumentWrittenDate(),
				statement.getCumulativeProgressRate(),
				statement.getStatusCode(),
				toSourceFile(statement.getSourceFileId()),
				summaries,
				itemResponses
		);
	}

	private UsageStatementSummaryResponse toSummaryResponse(UsageStatementSummary summary, Map<String, String> categoryNames) {
		return new UsageStatementSummaryResponse(
				summary.getCategoryCode(),
				categoryNames.getOrDefault(summary.getCategoryCode(), summary.getCategoryCode()),
				summary.getPreviousAmount(),
				summary.getCurrentAmount(),
				summary.getCumulativeAmount()
		);
	}

	private SourceFileResponse toSourceFile(Long sourceFileId) {
		if (sourceFileId == null) {
			return null;
		}
		return fileRepository.findById(sourceFileId)
				.map(file -> new SourceFileResponse(
						file.getId(),
						file.getOriginalFilename(),
						file.getUploadedEvidenceTypeCode(),
						file.getMimeType(),
						file.getSizeBytes(),
						file.getUploadedAt()
				))
				.orElse(null);
	}

	@Transactional
	public UsageStatementStatusResponse submit(Long currentUserId, Long projectId, Long statementId) {
		projectAccessService.requireWritable(currentUserId, projectId);
		UsageStatement statement = statementRepository.findByIdAndProjectId(statementId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
		statement.submit();
		// TODO: SHE 담당자 알림 발송 (R-33, 알림 서비스 구현 후 연동)
		return new UsageStatementStatusResponse(statement.getId(), statement.getStatusCode());
	}

	@Transactional
	public UsageStatementStatusResponse requestSupplement(Long currentUserId, Long projectId, Long statementId) {
		projectAccessService.requireAdmin(currentUserId);
		requireLegalCompleted(statementId);
		UsageStatement statement = statementRepository.findByIdAndProjectId(statementId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
		statement.requestSupplement();
		return new UsageStatementStatusResponse(statement.getId(), statement.getStatusCode());
	}

	@Transactional
	public UsageStatementStatusResponse completeReview(Long currentUserId, Long projectId, Long statementId) {
		projectAccessService.requireAdmin(currentUserId);
		requireLegalCompleted(statementId);
		UsageStatement statement = statementRepository.findByIdAndProjectId(statementId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
		statement.completeReview();
		return new UsageStatementStatusResponse(statement.getId(), statement.getStatusCode());
	}

	private void requireLegalCompleted(Long statementId) {
		if (!agentLogRepository.existsStatementLogWithSuccessOrHil(statementId, "legal")) {
			throw new ApiException(HttpStatus.CONFLICT, "법령 검토가 완료된 후에 진행할 수 있습니다.");
		}
	}

	private LocalDate toReportMonth(int year, int month) {
		try {
			return YearMonth.of(year, month).atDay(1);
		} catch (DateTimeException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "year와 month가 올바르지 않습니다.");
		}
	}
}
