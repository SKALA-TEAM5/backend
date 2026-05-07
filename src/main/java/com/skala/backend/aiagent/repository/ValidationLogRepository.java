package com.skala.backend.aiagent.repository;

import com.skala.backend.aiagent.domain.ValidationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ValidationLogRepository extends JpaRepository<ValidationLog, Long> {
}
