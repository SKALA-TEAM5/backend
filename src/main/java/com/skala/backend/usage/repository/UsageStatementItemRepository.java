package com.skala.backend.usage.repository;

import com.skala.backend.usage.domain.UsageStatementItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsageStatementItemRepository extends JpaRepository<UsageStatementItem, Long> {

	List<UsageStatementItem> findByUsageStatementIdOrderByPageNoAscUsedOnAscIdAsc(Long usageStatementId);

	List<UsageStatementItem> findByUsageStatementIdAndCategoryCodeOrderByPageNoAscUsedOnAscIdAsc(Long usageStatementId, String categoryCode);

	long countByUsageStatementId(Long usageStatementId);

	@Query("""
			SELECT i
			FROM UsageStatementItem i
			JOIN UsageStatement s ON s.id = i.usageStatementId
			WHERE i.id = :itemId
				AND s.projectId = :projectId
			""")
	Optional<UsageStatementItem> findProjectItem(@Param("projectId") Long projectId, @Param("itemId") Long itemId);

	@Query("""
			SELECT i
			FROM UsageStatementItem i
			JOIN UsageStatement s ON s.id = i.usageStatementId
			WHERE s.projectId = :projectId
				AND i.categoryCode = :categoryCode
			ORDER BY i.pageNo ASC, i.usedOn ASC, i.id ASC
			""")
	List<UsageStatementItem> findProjectItemsByCategory(@Param("projectId") Long projectId, @Param("categoryCode") String categoryCode);
}
