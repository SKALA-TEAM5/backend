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
}
