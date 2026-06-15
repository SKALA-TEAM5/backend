package com.skala.backend.file.service;

import com.skala.backend.evidence.service.EvidenceCommandService;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.evidence.dto.EvidenceRequests.LinkEvidenceFileRequest;
import com.skala.backend.evidence.dto.EvidenceResponses.EvidenceLinkResponse;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileListResponse;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileResponse;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileUploadResponse;
import com.skala.backend.file.dto.ProjectFileResponses.UploadAndLinkResponse;
import com.skala.backend.file.dto.ProjectFileResponses.VisionDetectionsResponse;
import com.skala.backend.file.dto.ProjectFileResponses.VisionDetections;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.CodeLookupService;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.repository.UsageStatementRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ProjectFileService {

	private static final Logger log = LoggerFactory.getLogger(ProjectFileService.class);

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;

	// 사용내역서는 OCR/파싱 대상이라 PDF만 허용한다. 다른 증빙(영수증·사진 등)은 제약 없음.
	private static final String USAGE_STATEMENT_TYPE_CODE = "usage_statement";
	private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

	private final ProjectAccessService projectAccessService;
	private final ProjectFileRepository fileRepository;
	private final CodeLookupService codeLookupService;
	private final EvidenceCommandService evidenceCommandService;
	private final UsageStatementRepository usageStatementRepository;
	private final MinioClient minioClient;
	private final VisionDetectionParser visionDetectionParser;
	private final String bucket;

	public ProjectFileService(
			ProjectAccessService projectAccessService,
			ProjectFileRepository fileRepository,
			CodeLookupService codeLookupService,
			EvidenceCommandService evidenceCommandService,
			UsageStatementRepository usageStatementRepository,
			MinioClient minioClient,
			VisionDetectionParser visionDetectionParser,
			@Value("${app.file-storage.bucket}") String bucket
	) {
		this.projectAccessService = projectAccessService;
		this.fileRepository = fileRepository;
		this.codeLookupService = codeLookupService;
		this.evidenceCommandService = evidenceCommandService;
		this.usageStatementRepository = usageStatementRepository;
		this.minioClient = minioClient;
		this.visionDetectionParser = visionDetectionParser;
		this.bucket = bucket;
	}

	@Transactional(readOnly = true)
	public ProjectFileListResponse listFiles(Long currentUserId, Long projectId, String evidenceTypeCode, Boolean linked, Integer page, Integer size) {
		projectAccessService.requireReadable(currentUserId, projectId);
		if (evidenceTypeCode != null && !codeLookupService.evidenceTypeExists(evidenceTypeCode)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 증빙 타입입니다.");
		}
		int pageNumber = validatePage(page);
		int pageSize = validateSize(size);

		List<ProjectFile> files = fileRepository.searchProjectFiles(
				projectId,
				evidenceTypeCode,
				linked,
				pageSize,
				(pageNumber - 1) * pageSize
		);
		Map<String, String> evidenceTypeNames = codeLookupService.evidenceTypeNames();
		Map<Long, Long> linkedCounts = codeLookupService.linkedItemCounts(files.stream().map(ProjectFile::getId).toList());
		List<ProjectFileResponse> items = files.stream()
				.map(file -> toFileResponse(file, evidenceTypeNames, linkedCounts))
				.toList();
		return new ProjectFileListResponse(projectId, items);
	}

	@Transactional
	public ProjectFileUploadResponse upload(Long currentUserId, Long projectId, String evidenceTypeCode, MultipartFile multipartFile, Instant capturedAt) {
		projectAccessService.requireWritable(currentUserId, projectId);
		if (!codeLookupService.evidenceTypeExists(evidenceTypeCode)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 증빙 타입입니다.");
		}
		if (multipartFile == null || multipartFile.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "file은 필수입니다.");
		}

		String originalFilename = multipartFile.getOriginalFilename() == null ? "upload" : multipartFile.getOriginalFilename();
		String mimeType = multipartFile.getContentType() == null ? "application/octet-stream" : multipartFile.getContentType();

		if (USAGE_STATEMENT_TYPE_CODE.equals(evidenceTypeCode)) {
			requirePdf(originalFilename, mimeType, multipartFile);
		}
		String storageKey = storageKey(projectId, originalFilename);

		try (InputStream inputStream = multipartFile.getInputStream()) {
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(bucket)
							.object(storageKey)
							.stream(inputStream, multipartFile.getSize(), -1)
							.contentType(mimeType)
							.build()
			);
		} catch (Exception e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.");
		}

		ProjectFile file = fileRepository.save(ProjectFile.create(
				projectId,
				currentUserId,
				evidenceTypeCode,
				originalFilename,
				storageKey,
				mimeType,
				multipartFile.getSize(),
				capturedAt
		));
		return new ProjectFileUploadResponse(
				file.getId(),
				file.getOriginalFilename(),
				file.getUploadedEvidenceTypeCode(),
				file.getMimeType(),
				file.getSizeBytes(),
				file.getUploadedAt()
		);
	}

	@Transactional(readOnly = true)
	public VisionDetectionsResponse getVisionDetections(Long currentUserId, Long projectId, Long fileId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		ProjectFile file = requireFile(projectId, fileId);
		VisionDetections detections = visionDetectionParser.parse(file.getDetail(), file.getUploadedEvidenceTypeCode());
		return new VisionDetectionsResponse(fileId, detections);
	}

	@Transactional(readOnly = true)
	public FileResource download(Long currentUserId, Long projectId, Long fileId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		ProjectFile file = requireFile(projectId, fileId);
		return toResource(file, false);
	}

	@Transactional(readOnly = true)
	public FileResource preview(Long currentUserId, Long projectId, Long fileId) {
		projectAccessService.requireReadable(currentUserId, projectId);
		ProjectFile file = requireFile(projectId, fileId);
		if (!file.getMimeType().equals("application/pdf") && !file.getMimeType().startsWith("image/")) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "미리보기를 지원하지 않는 파일 형식입니다.");
		}
		return toResource(file, true);
	}

	@Transactional
	public UploadAndLinkResponse uploadAndLink(Long currentUserId, Long projectId, Long itemId, String evidenceTypeCode, MultipartFile multipartFile, Instant capturedAt) {
		ProjectFileUploadResponse uploaded = upload(currentUserId, projectId, evidenceTypeCode, multipartFile, capturedAt);
		EvidenceLinkResponse linked = evidenceCommandService.linkFile(
				currentUserId, projectId, itemId,
				new LinkEvidenceFileRequest(uploaded.fileId(), evidenceTypeCode)
		);
		return new UploadAndLinkResponse(uploaded.fileId(), linked.linkId());
	}

	@Transactional
	public void rename(Long currentUserId, Long projectId, Long fileId, String originalFilename) {
		projectAccessService.requireAssignedMember(currentUserId, projectId);
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "파일명은 필수입니다.");
		}
		if (originalFilename.length() > 500) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "파일명은 500자 이하여야 합니다.");
		}
		ProjectFile file = requireFile(projectId, fileId);
		file.rename(originalFilename);
		evidenceCommandService.revertDraftForFileLinks(fileId);
	}

	@Transactional
	public void delete(Long currentUserId, Long projectId, Long fileId) {
		projectAccessService.requireWritable(currentUserId, projectId);
		ProjectFile file = requireFile(projectId, fileId);
		String storageKey = file.getStorageKey(); // 엔티티 삭제 전에 키 확보 (제거된 엔티티 getter 의존 방지)
		evidenceCommandService.deleteLinksForFile(file.getId());
		usageStatementRepository.clearSourceFileId(file.getId());
		fileRepository.delete(file);
		// DB 삭제를 먼저 확정(flush)해 제약 위반을 표면화한 뒤 MinIO를 지운다.
		// → MinIO만 지워지고 DB 행이 남는 역방향 고아를 방지. MinIO 실패 시 예외로 전체 롤백.
		fileRepository.flush();
		removeObject(storageKey);
	}

	/**
	 * 트랜잭션 커밋 이후 호출되는 best-effort MinIO 오브젝트 회수.
	 * DB는 이미 삭제 확정된 상태이므로, 실패해도 예외를 던지지 않고 삼킨다(무해한 스토리지 찌꺼기로 남김).
	 * 사용내역서 삭제(UsageStatementService.delete)의 afterCommit 단계에서 사용한다.
	 */
	public void removeObjectsQuietly(java.util.Collection<String> storageKeys) {
		for (String storageKey : storageKeys) {
			try {
				minioClient.removeObject(
						RemoveObjectArgs.builder()
								.bucket(bucket)
								.object(storageKey)
								.build()
				);
			} catch (Exception e) {
				log.warn("MinIO 오브젝트 회수 실패(무해한 찌꺼기로 남김): {}", storageKey, e);
			}
		}
	}

	private void removeObject(String storageKey) {
		// 주의: S3/MinIO removeObject(DeleteObject)는 멱등이라 키가 없거나 틀려도 예외 없이 성공한다.
		// 따라서 "성공 응답 = 실제 삭제"가 아니다. 네트워크·권한 등 실제 오류만 예외로 올라오며, 이는 마스킹하지 않고 그대로 전파한다.
		try {
			minioClient.removeObject(
					RemoveObjectArgs.builder()
							.bucket(bucket)
							.object(storageKey)
							.build()
			);
		} catch (Exception e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 삭제에 실패했습니다.");
		}
	}

	// 사용내역서 업로드 가드: 확장자·MIME·매직넘버(%PDF) 3중으로 PDF만 통과시킨다.
	// 프론트 검증은 직접 API 호출로 우회 가능하므로 저장 직전 백엔드에서 한 번 더 막는다.
	private void requirePdf(String originalFilename, String mimeType, MultipartFile multipartFile) {
		boolean pdfExtension = originalFilename.toLowerCase().endsWith(".pdf");
		boolean pdfMime = "application/pdf".equals(mimeType)
				|| "application/octet-stream".equals(mimeType);
		if (!pdfExtension || !pdfMime || !hasPdfMagic(multipartFile)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "사용내역서는 PDF 파일만 업로드할 수 있습니다.");
		}
	}

	private boolean hasPdfMagic(MultipartFile multipartFile) {
		byte[] header = new byte[PDF_MAGIC.length];
		try (InputStream inputStream = multipartFile.getInputStream()) {
			int read = inputStream.readNBytes(header, 0, PDF_MAGIC.length);
			if (read < PDF_MAGIC.length) {
				return false;
			}
		} catch (Exception e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽을 수 없습니다.");
		}
		return java.util.Arrays.equals(header, PDF_MAGIC);
	}

	private ProjectFile requireFile(Long projectId, Long fileId) {
		return fileRepository.findByIdAndProjectId(fileId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다."));
	}

	private FileResource toResource(ProjectFile file, boolean inline) {
		try {
			InputStream stream = minioClient.getObject(
					GetObjectArgs.builder()
							.bucket(bucket)
							.object(file.getStorageKey())
							.build()
			);
			return new FileResource(new InputStreamResource(stream), file.getOriginalFilename(), file.getMimeType(), inline);
		} catch (ErrorResponseException e) {
			if ("NoSuchKey".equals(e.errorResponse().code())) {
				throw new ApiException(HttpStatus.NOT_FOUND, "저장된 파일을 찾을 수 없습니다.");
			}
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽을 수 없습니다.");
		} catch (Exception e) {
			throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "파일을 읽을 수 없습니다.");
		}
	}

	private ProjectFileResponse toFileResponse(ProjectFile file, Map<String, String> evidenceTypeNames, Map<Long, Long> linkedCounts) {
		return new ProjectFileResponse(
				file.getId(),
				file.getUploadedEvidenceTypeCode(),
				evidenceTypeNames.getOrDefault(file.getUploadedEvidenceTypeCode(), file.getUploadedEvidenceTypeCode()),
				file.getOriginalFilename(),
				file.getMimeType(),
				file.getSizeBytes(),
				file.getCapturedAt(),
				file.getUploadedAt(),
				file.getStatusCode(),
				linkedCounts.getOrDefault(file.getId(), 0L)
		);
	}

	private String storageKey(Long projectId, String originalFilename) {
		String extension = "";
		int dotIndex = originalFilename.lastIndexOf('.');
		if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
			extension = originalFilename.substring(dotIndex);
		}
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		return "projects/%d/%s/%s%s".formatted(projectId, today, UUID.randomUUID(), extension);
	}

	private int validatePage(Integer page) {
		int value = page == null ? 1 : page;
		if (value < 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "page는 1 이상이어야 합니다.");
		}
		return value;
	}

	private int validateSize(Integer size) {
		int value = size == null ? DEFAULT_PAGE_SIZE : size;
		if (value < 1 || value > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "size는 1 이상 50 이하여야 합니다.");
		}
		return value;
	}

	public record FileResource(Resource resource, String filename, String mimeType, boolean inline) {
	}
}
