package io.resourcelane.wallet_service.service;

import io.resourcelane.wallet_service.dto.TradeDTO;
import io.resourcelane.wallet_service.dto.WalletDTO;

/**
 * Interface defining the contract for the Wallet Service.
 * Outlines the necessary methods for wallet lifecycle management and transaction processing,
 * such as querying balances, creating wallets, and handling fund/units transfers.
 */
public interface WalletService {

    /**
     * Retrieves the wallet for a given user.
     *
     * @param userId The user ID.
     * @return The WalletDTO containing wallet details.
     */
    WalletDTO getWallet(Long userId);

    /**
     * Creates a new wallet for a user.
     *
     * @param userId The user ID.
     * @return The created WalletDTO.
     */
    WalletDTO createWallet(Long userId);

    /**
     * Adds money to a user's wallet.
     *
     * @param userId The user ID.
     * @param amount The amount to add.
     */
    void addMoney(Long userId, Double amount);

    /**
     * Withdraws money from a user's wallet.
     *
     * @param userId The user ID.
     * @param amount The amount to withdraw.
     */
    void withdrawMoney(Long userId, Double amount);

    /**
     * Updates wallets for both parties in a trade.
     *
     * @param tradeDTO The trade details.
     */
    void updateWallets(TradeDTO tradeDTO);

    /**
     * Adds units to a user's wallet.
     *
     * @param userId   The user ID.
     * @param quantity The quantity of units to add.
     */
    void addUnits(Long userId, int quantity);

    /**
     * Withdraws units from a user's wallet.
     *
     * @param userId   The user ID.
     * @param quantity The quantity of units to withdraw.
     */
    void withdrawUnits(Long userId, int quantity);

}