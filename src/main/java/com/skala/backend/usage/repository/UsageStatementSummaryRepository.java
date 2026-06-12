package com.skala.backend.usage.repository;

import com.skala.backend.usage.domain.UsageStatementSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UsageStatementSummaryRepository extends JpaRepository<UsageStatementSummary, Long> {

	List<UsageStatementSummary> findByUsageStatementIdOrderByCategoryCodeAsc(Long usageStatementId);

	long countByUsageStatementId(Long usageStatementId);

	@Modifying
	@Query("DELETE FROM UsageStatementSummary s WHERE s.usageStatementId = :usageStatementId")
	void deleteByUsageStatementId(@Param("usageStatementId") Long usageStatementId);
}
