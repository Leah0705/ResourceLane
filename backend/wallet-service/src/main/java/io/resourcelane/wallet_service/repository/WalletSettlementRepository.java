package io.resourcelane.wallet_service.repository;

import io.resourcelane.wallet_service.model.WalletSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletSettlementRepository extends JpaRepository<WalletSettlement, String> {
}
