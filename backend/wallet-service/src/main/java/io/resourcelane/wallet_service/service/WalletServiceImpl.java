package io.resourcelane.wallet_service.service;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

import io.resourcelane.wallet_service.dto.TradeDTO;
import io.resourcelane.wallet_service.dto.WalletDTO;
import io.resourcelane.wallet_service.model.Wallet;
import io.resourcelane.wallet_service.model.WalletSettlement;
import io.resourcelane.wallet_service.model.OrderReservation;
import io.resourcelane.wallet_service.repository.WalletRepositoryWrapper;
import io.resourcelane.wallet_service.repository.WalletSettlementRepository;
import io.resourcelane.wallet_service.repository.OrderReservationRepository;
import java.util.List;

/**
 * Implementation of the {@link WalletService} interface.
 * Handles core business logic for wallet operations such as creation, retrieving balances, 
 * adding/withdrawing funds and units, as well as orchestrating transaction processing for trades.
 */
@Service
public class WalletServiceImpl implements WalletService {
    
    /**
     * Data access wrapper for wallet entities.
     */
    private final WalletRepositoryWrapper walletRepository;
    private final WalletSettlementRepository settlementRepository;
    private final OrderReservationRepository reservationRepository;

    /**
     * Constructs a new WalletServiceImpl with the required repository dependency.
     * 
     * @param walletRepository The repository wrapper used for database operations related to wallets.
     */
    public WalletServiceImpl(WalletRepositoryWrapper walletRepository,
                             WalletSettlementRepository settlementRepository,
                             OrderReservationRepository reservationRepository) {
        this.walletRepository = walletRepository;
        this.settlementRepository = settlementRepository;
        this.reservationRepository = reservationRepository;
    }

    /**
     * Retrieves the wallet details for a specified user.
     *
     * @param userId The unique identifier of the user whose wallet is to be retrieved.
     * @return A WalletDTO containing the user's wallet information, or null if the wallet does not exist.
     */
    @Override
    public WalletDTO getWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId);

        if (wallet == null) {
            return null;
        }

        WalletDTO walletDTO = new WalletDTO();
        BeanUtils.copyProperties(wallet, walletDTO);
        walletDTO.setBalance(wallet.getBalance().doubleValue());
        return walletDTO;
    }

    /**
     * Creates and initializes a new wallet for a user with zero balance and zero units.
     * This is typically called when a user registers or requires a new wallet.
     *
     * @param userId The unique identifier of the user for whom the wallet will be created.
     * @return A WalletDTO representing the newly created wallet.
     */
    @Override
    public WalletDTO createWallet(Long userId) {
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(new BigDecimal("0.00"));
        wallet.setUnits(0.0);

        Wallet savedWallet = walletRepository.save(wallet);

        WalletDTO walletDTO = new WalletDTO();
        BeanUtils.copyProperties(savedWallet, walletDTO);
        walletDTO.setBalance(savedWallet.getBalance().doubleValue());
        return walletDTO;
    }

    /**
     * Executes the wallet portion of a trade by updating the wallets of both the buyer and seller.
     * The operation is transactional to ensure that money and units are transferred atomically.
     *
     * @param tradeDTO The data transfer object containing the details of the trade (buyer ID, seller ID, price, quantity).
     */
    @Override
    @Transactional
    public void updateWallets(TradeDTO tradeDTO) {
        if (tradeDTO.getExecutionId() == null || tradeDTO.getExecutionId().isBlank()
                || tradeDTO.getExecutionId().length() > 128
                || tradeDTO.getBuyOrderId() == null || tradeDTO.getSellOrderId() == null
                || tradeDTO.getPrice() == null || !Double.isFinite(tradeDTO.getPrice())
                || tradeDTO.getPrice() <= 0 || tradeDTO.getQuantity() <= 0) {
            throw new IllegalArgumentException("Invalid trade execution");
        }

        Long buyUserId = Long.parseLong(tradeDTO.getBuyUserId());
        Long sellUserId = Long.parseLong(tradeDTO.getSellUserId());
        if (buyUserId.equals(sellUserId)) {
            throw new IllegalArgumentException("Self trade is not supported");
        }

        // Lock in the same order for every trade. A concurrent duplicate waits here,
        // then observes the committed settlement marker after the first transfer.
        Wallet first = walletRepository.findByUserIdForUpdate(Math.min(buyUserId, sellUserId));
        Wallet second = walletRepository.findByUserIdForUpdate(Math.max(buyUserId, sellUserId));
        if (first == null || second == null) {
            throw new IllegalArgumentException("Wallet not found for user");
        }
        Wallet buyer = buyUserId < sellUserId ? first : second;
        Wallet seller = buyUserId < sellUserId ? second : first;

        WalletSettlement previous = settlementRepository.findById(tradeDTO.getExecutionId()).orElse(null);
        if (previous != null) {
            if (!previous.matches(tradeDTO)) {
                throw new IllegalArgumentException("Execution ID reused with different trade details");
            }
            return;
        }

        BigDecimal price = money(tradeDTO.getPrice());
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(tradeDTO.getQuantity()));
        OrderReservation buyHold = reservationRepository.findById(Long.parseLong(tradeDTO.getBuyOrderId()))
                .orElseThrow(() -> new IllegalArgumentException("Buy reservation not found"));
        OrderReservation sellHold = reservationRepository.findById(Long.parseLong(tradeDTO.getSellOrderId()))
                .orElseThrow(() -> new IllegalArgumentException("Sell reservation not found"));
        if (!"BUY".equals(buyHold.getSide()) || !buyUserId.equals(buyHold.getUserId())
                || !"SELL".equals(sellHold.getSide()) || !sellUserId.equals(sellHold.getUserId())
                || buyHold.heldQuantity() < tradeDTO.getQuantity()
                || sellHold.heldQuantity() < tradeDTO.getQuantity()
                || price.compareTo(buyHold.getPrice()) > 0) {
            throw new IllegalArgumentException("Execution exceeds order reservation");
        }
        if (buyer.getBalance().compareTo(totalAmount) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        if (seller.getUnits() < tradeDTO.getQuantity()) {
            throw new IllegalArgumentException("Insufficient units");
        }

        buyer.setBalance(buyer.getBalance().subtract(totalAmount));
        buyer.setUnits(buyer.getUnits() + tradeDTO.getQuantity());
        seller.setBalance(seller.getBalance().add(totalAmount));
        seller.setUnits(seller.getUnits() - tradeDTO.getQuantity());
        buyHold.setSettledQuantity(buyHold.getSettledQuantity() + tradeDTO.getQuantity());
        sellHold.setSettledQuantity(sellHold.getSettledQuantity() + tradeDTO.getQuantity());
        walletRepository.save(buyer);
        walletRepository.save(seller);
        reservationRepository.save(buyHold);
        reservationRepository.save(sellHold);
        settlementRepository.saveAndFlush(new WalletSettlement(tradeDTO));
    }

    /**
     * Adds a specified monetary amount to a user's wallet balance.
     * This method runs within a transaction to maintain consistency.
     *
     * @param userId The unique identifier of the user.
     * @param amount The amount of money to add. Must be greater than zero.
     * @throws IllegalArgumentException If the specified user's wallet is not found.
     */
    @Override
    @Transactional
    public void addMoney(Long userId, Double amount) {
        BigDecimal deposit = money(amount);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId);
        if (wallet != null) {
            wallet.setBalance(wallet.getBalance().add(deposit));
            walletRepository.save(wallet);
        } else {
           throw new IllegalArgumentException("Wallet not found for user: " + userId);
        }
    }

    /**
     * Deducts a specified monetary amount from a user's wallet balance.
     * It ensures the user has sufficient balance before proceeding to prevent overdrafts.
     *
     * @param userId The unique identifier of the user.
     * @param amount The amount of money to withdraw.
     * @throws IllegalArgumentException If the wallet is not found or if the user has insufficient balance.
     */
    @Override
    @Transactional
    public void withdrawMoney(Long userId, Double amount) {
        BigDecimal withdrawal = money(amount);
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId);
        if (wallet != null && wallet.getBalance().subtract(heldMoney(userId)).compareTo(withdrawal) >= 0) {
            wallet.setBalance(wallet.getBalance().subtract(withdrawal));
            walletRepository.save(wallet);
        } else {
            throw new IllegalArgumentException("Insufficient balance");
        }
    }

    /**
     * Credits a specified quantity of units to a user's wallet.
     * This operation is primarily used when a user buys units or makes a deposit.
     *
     * @param userId   The unique identifier of the user.
     * @param quantity The quantity of units to add.
     * @throws IllegalArgumentException If the specified user's wallet is not found.
     */
    @Override
    @Transactional
    public void addUnits(Long userId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Units quantity must be positive");
        }
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId);
        if (wallet != null) {
            wallet.setUnits(wallet.getUnits() + quantity);
            walletRepository.save(wallet);
        } else {
            throw new IllegalArgumentException("Wallet not found for user: " + userId);
        }
    }

    /**
     * Deducts a specified quantity of units from a user's wallet.
     * Ensures the user has sufficient units to complete the withdrawal to prevent negative balances.
     *
     * @param userId   The unique identifier of the user.
     * @param quantity The quantity of units to withdraw.
     * @throws IllegalArgumentException If the wallet is not found or if there is insufficient units in the wallet.
     */
    @Override
    @Transactional
    public void withdrawUnits(Long userId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Units quantity must be positive");
        }
        Wallet wallet = walletRepository.findByUserIdForUpdate(userId);
        if (wallet != null && wallet.getUnits() - heldUnits(userId) >= quantity) {
            wallet.setUnits(wallet.getUnits() - quantity);
            walletRepository.save(wallet);
        } else {
            throw new IllegalArgumentException("Insufficient units");
        }
    }

    private static BigDecimal money(Double amount) {
        if (amount == null || !Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive and finite");
        }
        try {
            return BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount must have at most two decimal places", e);
        }
    }

    private BigDecimal heldMoney(Long userId) {
        List<OrderReservation> reservations = reservationRepository.findByUserIdAndSide(userId, "BUY");
        return reservations.stream()
                .map(r -> r.getPrice().multiply(BigDecimal.valueOf(r.heldQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int heldUnits(Long userId) {
        return reservationRepository.findByUserIdAndSide(userId, "SELL").stream()
                .mapToInt(OrderReservation::heldQuantity).sum();
    }

}
