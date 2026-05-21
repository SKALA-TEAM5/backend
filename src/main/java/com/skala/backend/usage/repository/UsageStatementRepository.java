package com.skala.backend.usage.repository;

import com.skala.backend.usage.domain.UsageStatement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UsageStatementRepository extends JpaRepository<UsageStatement, Long> {

	Optional<UsageStatement> findFirstByProjectIdOrderByReportMonthDescRevisionNoDesc(Long projectId);

	Optional<UsageStatement> findFirstByProjectIdAndReportMonthOrderByRevisionNoDesc(Long projectId, LocalDate reportMonth);

	Optional<UsageStatement> findByIdAndProjectId(Long id, Long projectId);

	List<UsageStatement> findByProjectIdOrderByReportMonthDescRevisionNoDesc(Long projectId);

	boolean existsByIdAndProjectId(Long id, Long projectId);
}
