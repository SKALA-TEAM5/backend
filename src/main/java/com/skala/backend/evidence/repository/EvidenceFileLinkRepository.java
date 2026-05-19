package com.skala.backend.evidence.repository;

import com.skala.backend.evidence.domain.EvidenceFileLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvidenceFileLinkRepository extends JpaRepository<EvidenceFileLink, Long> {

	boolean existsByUsageStatementItemIdAndFileId(Long usageStatementItemId, Long fileId);

	List<EvidenceFileLink> findByUsageStatementItemId(Long usageStatementItemId);

	List<EvidenceFileLink> findByUsageStatementItemIdIn(Collection<Long> usageStatementItemIds);

	List<EvidenceFileLink> findByFileId(Long fileId);

	@Query("""
			SELECT l
			FROM EvidenceFileLink l
			JOIN UsageStatementItem i ON i.id = l.usageStatementItemId
			JOIN UsageStatement s ON s.id = i.usageStatementId
			WHERE l.id = :linkId
				AND s.projectId = :projectId
			""")
	Optional<EvidenceFileLink> findProjectLink(@Param("projectId") Long projectId, @Param("linkId") Long linkId);

	@Query(value = """
			SELECT COUNT(DISTINCT l.file_id)
			FROM service.evidence_file_links l
			JOIN service.usage_statement_items i ON i.id = l.usage_statement_item_id
			JOIN service.usage_statements s ON s.id = i.usage_statement_id
			JOIN service.files f ON f.id = l.file_id
			WHERE s.project_id = :projectId
				AND f.deleted_at IS NULL
				AND l.checked_at IS NULL
			""", nativeQuery = true)
	long countUncheckedMatchedFiles(@Param("projectId") Long projectId);

	@Modifying
	@Query(value = """
			UPDATE service.evidence_file_links l
			SET checked_at = now()
			FROM service.usage_statement_items i,
				service.usage_statements s,
				service.files f
			WHERE i.id = l.usage_statement_item_id
				AND s.id = i.usage_statement_id
				AND f.id = l.file_id
				AND s.project_id = :projectId
				AND f.deleted_at IS NULL
				AND l.checked_at IS NULL
			""", nativeQuery = true)
	int markProjectLinksChecked(@Param("projectId") Long projectId);

	@Modifying
	void deleteByFileId(Long fileId);

	@Modifying
	@Query("DELETE FROM EvidenceFileLink l WHERE l.usageStatementItemId = :itemId")
	void deleteByUsageStatementItemId(@Param("itemId") Long itemId);
}
