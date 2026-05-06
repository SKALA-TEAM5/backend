package com.skala.backend.evidence.repository;

import com.skala.backend.evidence.domain.EvidenceRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EvidenceRequirementRepository extends JpaRepository<EvidenceRequirement, Long> {

	List<EvidenceRequirement> findByUsageStatementItemIdAndActiveTrue(Long usageStatementItemId);

	List<EvidenceRequirement> findByUsageStatementItemIdInAndActiveTrue(Collection<Long> usageStatementItemIds);
}
