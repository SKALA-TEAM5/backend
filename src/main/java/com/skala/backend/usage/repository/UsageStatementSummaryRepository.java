package com.skala.backend.usage.repository;

import com.skala.backend.usage.domain.UsageStatementSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UsageStatementSummaryRepository extends JpaRepository<UsageStatementSummary, Long> {

	List<UsageStatementSummary> findByUsageStatementIdOrderByCategoryCodeAsc(Long usageStatementId);

	long countByUsageStatementId(Long usageStatementId);
}
