package io.resourcelane.wallet_service.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object (DTO) for conveying Wallet details to clients.
 * This object is used to decouple the internal database entity from the external API representation,
 * ensuring that only relevant data (balances and identifiers) is exposed over the network.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletDTO {
    
    /**
     * The primary key, auto-generated unique identifier for the wallet.
     */
    private Long walletId;

    /**
     * The unique identifier of the user who owns the wallet.
     */
    private Long userId;

    /**
     * The current simulated credit balance of the user.
     */
    private Double balance;

    /**
     * The current resource-unit balance of the user.
     */
    private Double units;
}
