package com.skala.backend.file.repository;

import com.skala.backend.file.domain.ProjectFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectFileRepository extends JpaRepository<ProjectFile, Long> {

	Optional<ProjectFile> findByIdAndProjectId(Long id, Long projectId);

	List<ProjectFile> findByIdIn(Collection<Long> ids);

	@Query(value = """
			SELECT f.*
			FROM service.files f
			WHERE f.project_id = :projectId
				AND (CAST(:evidenceTypeCode AS text) IS NULL OR f.uploaded_evidence_type_code = :evidenceTypeCode)
				AND (
					CAST(:linked AS boolean) IS NULL
					OR (:linked = true AND EXISTS (
						SELECT 1 FROM service.evidence_file_links efl WHERE efl.file_id = f.id
					))
					OR (:linked = false AND NOT EXISTS (
						SELECT 1 FROM service.evidence_file_links efl WHERE efl.file_id = f.id
					))
				)
			ORDER BY f.uploaded_at DESC, f.id DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<ProjectFile> searchProjectFiles(
			@Param("projectId") Long projectId,
			@Param("evidenceTypeCode") String evidenceTypeCode,
			@Param("linked") Boolean linked,
			@Param("limit") int limit,
			@Param("offset") int offset
	);

	/**
	 * 후보 파일 중 더 이상 어떤 증빙 링크·사용내역서 원본으로도 참조되지 않는(고아) 파일을 반환한다.
	 * 사용내역서 삭제 시 호출되며, 링크·항목·사용내역서 행을 모두 지운 뒤에 실행해야 정확히 판정된다.
	 * fileIds가 비어 있어도 = ANY(...)는 안전하게 0건을 매칭한다.
	 */
	@Query(value = """
			SELECT f.*
			FROM service.files f
			WHERE f.id = ANY(:fileIds)
				AND NOT EXISTS (SELECT 1 FROM service.evidence_file_links l WHERE l.file_id = f.id)
				AND NOT EXISTS (SELECT 1 FROM service.usage_statements s WHERE s.source_file_id = f.id)
			""", nativeQuery = true)
	List<ProjectFile> findUnreferencedFiles(@Param("fileIds") Long[] fileIds);
}
