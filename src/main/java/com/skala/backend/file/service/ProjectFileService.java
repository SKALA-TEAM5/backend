package com.skala.backend.file.service;

import com.skala.backend.evidence.service.EvidenceCommandService;
import com.skala.backend.file.domain.ProjectFile;
import com.skala.backend.evidence.dto.EvidenceRequests.LinkEvidenceFileRequest;
import com.skala.backend.evidence.dto.EvidenceResponses.EvidenceLinkResponse;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileListResponse;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileResponse;
import com.skala.backend.file.dto.ProjectFileResponses.ProjectFileUploadResponse;
import com.skala.backend.file.dto.ProjectFileResponses.UploadAndLinkResponse;
import com.skala.backend.file.repository.ProjectFileRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.CodeLookupService;
import com.skala.backend.project.service.ProjectAccessService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
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

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;

	private final ProjectAccessService projectAccessService;
	private final ProjectFileRepository fileRepository;
	private final CodeLookupService codeLookupService;
	private final EvidenceCommandService evidenceCommandService;
	private final MinioClient minioClient;
	private final String bucket;

	public ProjectFileService(
			ProjectAccessService projectAccessService,
			ProjectFileRepository fileRepository,
			CodeLookupService codeLookupService,
			EvidenceCommandService evidenceCommandService,
			MinioClient minioClient,
			@Value("${app.file-storage.bucket}") String bucket
	) {
		this.projectAccessService = projectAccessService;
		this.fileRepository = fileRepository;
		this.codeLookupService = codeLookupService;
		this.evidenceCommandService = evidenceCommandService;
		this.minioClient = minioClient;
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
				file.getUploadedAt(),
				file.getDetail()
		);
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
	public void delete(Long currentUserId, Long projectId, Long fileId) {
		projectAccessService.requireWritable(currentUserId, projectId);
		ProjectFile file = requireFile(projectId, fileId);
		evidenceCommandService.deleteLinksForFile(file.getId());
		file.markDeleted(currentUserId);
	}

	private ProjectFile requireFile(Long projectId, Long fileId) {
		return fileRepository.findByIdAndProjectIdAndDeletedAtIsNull(fileId, projectId)
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
				linkedCounts.getOrDefault(file.getId(), 0L),
				file.getDetail()
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
