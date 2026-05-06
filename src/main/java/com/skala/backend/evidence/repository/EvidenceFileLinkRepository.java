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

	@Modifying
	void deleteByFileId(Long fileId);
}
