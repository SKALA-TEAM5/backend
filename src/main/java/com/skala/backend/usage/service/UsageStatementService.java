package com.skala.backend.usage.service;

import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.agent.repository.AgentUsageRecordRepository;
import com.skala.backend.evidence.repository.EvidenceFileLinkRepository;
import com.skala.backend.evidence.repository.EvidenceRequirementRepository;
import com.skala.backend.evidence.service.EvidenceQueryService;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.file.service.ProjectFileService;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final EvidenceRequirementRepository requirementRepository;
	private final EvidenceFileLinkRepository linkRepository;
	private final AgentUsageRecordRepository agentUsageRecordRepository;
	private final ProjectFileService projectFileService;

	public UsageStatementService(
			ProjectAccessService projectAccessService,
			UsageStatementRepository statementRepository,
			UsageStatementSummaryRepository summaryRepository,
			UsageStatementItemRepository itemRepository,
			ProjectFileRepository fileRepository,
			EvidenceQueryService evidenceQueryService,
			CodeLookupService codeLookupService,
			AgentLogRepository agentLogRepository,
			EvidenceRequirementRepository requirementRepository,
			EvidenceFileLinkRepository linkRepository,
			AgentUsageRecordRepository agentUsageRecordRepository,
			ProjectFileService projectFileService
	) {
		this.projectAccessService = projectAccessService;
		this.statementRepository = statementRepository;
		this.summaryRepository = summaryRepository;
		this.itemRepository = itemRepository;
		this.fileRepository = fileRepository;
		this.evidenceQueryService = evidenceQueryService;
		this.codeLookupService = codeLookupService;
		this.agentLogRepository = agentLogRepository;
		this.requirementRepository = requirementRepository;
		this.linkRepository = linkRepository;
		this.agentUsageRecordRepository = agentUsageRecordRepository;
		this.projectFileService = projectFileService;
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
		Map<Long, Long> summaryCounts = codeLookupService.summaryCountsByStatement(statementIds);
		Map<Long, Long> itemCounts = codeLookupService.itemCountsByStatement(statementIds);
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
						summaryCounts.getOrDefault(statement.getId(), 0L),
						itemCounts.getOrDefault(statement.getId(), 0L),
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
		requireLegalRan(statementId);
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

	/**
	 * 사용내역서 삭제 및 연결 데이터 정리.
	 * 권한은 쓰기 가능자(배정 admin·user, agent). 상태와 무관하게 삭제 가능.
	 *
	 * 정리 순서(자식 → 부모): agent_logs → evidence_requirements → evidence_file_links
	 * → usage_statement_items → usage_statement_summaries → agent_usage_records(참조 NULL)
	 * → usage_statements(todos는 FK CASCADE) → 고아 파일(DB) → (커밋 후) MinIO 오브젝트.
	 *
	 * 관계형 데이터는 단일 트랜잭션으로 원자적으로 정리되고, 원자화 불가능한 MinIO 오브젝트 제거만
	 * 커밋 이후 best-effort로 수행한다. 실패 시 무해한 스토리지 찌꺼기로 남으며 삭제는 성공 처리된다.
	 */
	@Transactional
	public void delete(Long currentUserId, Long projectId, Long statementId) {
		projectAccessService.requireWritable(currentUserId, projectId);
		UsageStatement statement = statementRepository.findByIdAndProjectId(statementId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));

		List<Long> itemIds = itemRepository.findIdsByUsageStatementId(statementId);
		Long[] itemIdArray = itemIds.toArray(Long[]::new);

		// 삭제 전에 후보 파일 수집: 원본 PDF + 항목에 연결된 증빙 파일
		Set<Long> candidateFileIds = new HashSet<>();
		if (statement.getSourceFileId() != null) {
			candidateFileIds.add(statement.getSourceFileId());
		}
		if (!itemIds.isEmpty()) {
			candidateFileIds.addAll(linkRepository.findFileIdsByItemIds(itemIds));
		}

		// 자식 → 부모 순으로 관계형 데이터 제거
		agentLogRepository.deleteByStatementOrItems(statementId, itemIdArray);
		if (!itemIds.isEmpty()) {
			requirementRepository.deleteByUsageStatementItemIdIn(itemIds);
			linkRepository.deleteByUsageStatementItemIdIn(itemIds);
		}
		itemRepository.deleteByUsageStatementId(statementId);
		summaryRepository.deleteByUsageStatementId(statementId);
		agentUsageRecordRepository.clearUsageStatementId(statementId);
		statementRepository.delete(statement);          // todos는 FK ON DELETE CASCADE로 함께 제거
		statementRepository.flush();                    // 제약 위반을 커밋 전에 표면화

		// 더 이상 참조되지 않는(고아) 파일만 DB에서 제거하고 MinIO 회수 대상 키 확보
		List<ProjectFile> orphanFiles = candidateFileIds.isEmpty()
				? List.of()
				: fileRepository.findUnreferencedFiles(candidateFileIds.toArray(Long[]::new));
		List<String> storageKeys = orphanFiles.stream().map(ProjectFile::getStorageKey).toList();
		if (!orphanFiles.isEmpty()) {
			fileRepository.deleteAll(orphanFiles);
			fileRepository.flush();
		}

		// MinIO 오브젝트는 커밋 이후 best-effort로 회수(DB-스토리지 dual-write 분리)
		if (!storageKeys.isEmpty()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					projectFileService.removeObjectsQuietly(storageKeys);
				}
			});
		}
	}

	private void requireLegalRan(Long statementId) {
		if (!agentLogRepository.existsStatementLogWithAnyResult(statementId, "legal")) {
			throw new ApiException(HttpStatus.CONFLICT, "법령 검토가 완료된 후에 진행할 수 있습니다.");
		}
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
