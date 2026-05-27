package com.skala.backend.evidence.repository;

import com.skala.backend.evidence.domain.EvidenceRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EvidenceRequirementRepository extends JpaRepository<EvidenceRequirement, Long> {

	List<EvidenceRequirement> findByUsageStatementItemIdAndActiveTrue(Long usageStatementItemId);

	List<EvidenceRequirement> findByUsageStatementItemIdInAndActiveTrue(Collection<Long> usageStatementItemIds);

	@Modifying
	@Query("DELETE FROM EvidenceRequirement r WHERE r.usageStatementItemId = :itemId")
	void deleteByUsageStatementItemId(@Param("itemId") Long itemId);
}
