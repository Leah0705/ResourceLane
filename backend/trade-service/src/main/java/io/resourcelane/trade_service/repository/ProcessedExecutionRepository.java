package io.resourcelane.trade_service.repository;

import io.resourcelane.trade_service.model.ProcessedExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedExecutionRepository extends JpaRepository<ProcessedExecution, String> {
}
