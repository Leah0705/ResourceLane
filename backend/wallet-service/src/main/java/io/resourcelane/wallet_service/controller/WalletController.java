package io.resourcelane.wallet_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import io.resourcelane.wallet_service.dto.AddUnitsRequestDTO;
import io.resourcelane.wallet_service.dto.AddMoneyRequest;
import io.resourcelane.wallet_service.dto.TradeDTO;
import io.resourcelane.wallet_service.dto.WithdrawMoneyRequest;
import io.resourcelane.wallet_service.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;

import io.resourcelane.wallet_service.dto.WalletDTO;
import io.resourcelane.wallet_service.dto.WithdrawUnitsRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * REST Controller for managing user wallets within the Units Exchange platform.
 * Exposes endpoints for wallet creation, retrieval, and performing transactions
 * such as adding or withdrawing money and units. Also provides secure internal
 * endpoints for cross-service trade settlement.
 */
@RestController
@RequestMapping("api/wallet")
public class WalletController {

    private static final Logger logger = LoggerFactory.getLogger(WalletController.class);

    /**
     * Service layer dependency handling the core wallet logic.
     */
    private final WalletService walletService;

    /**
     * Secret token used to authenticate internal requests originating from other microservices
     * (e.g., the Trade Service during order execution).
     */
    @Value("${internal.secret.token}")
    private String internalSecretToken;

    /**
     * Constructor for dependency injection of WalletService.
     * 
     * @param walletService the business logic service for wallet operations.
     */
    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * Creates a new wallet for the currently authenticated user.
     * This is typically invoked upon user registration or when a user first attempts to use wallet features.
     * Prevents the creation of duplicate wallets for a single user.
     *
     * @param authentication The Spring Security authentication object containing the user's principal (userId).
     * @param request        The current HTTP request context.
     * @return A ResponseEntity containing a success message and the newly created wallet data, 
     *         or an error response if creation fails (e.g., wallet already exists).
     */
    @PostMapping("/createWallet")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createWallet(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User not authenticated"));
        }

        Long userId = (Long) authentication.getPrincipal();

        // Check if wallet already exists
        WalletDTO existingWallet = walletService.getWallet(userId);
        if (existingWallet != null) {
            logger.warn("Wallet creation attempt failed: Wallet already exists for user {}", userId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Wallet already exists"));
        }

        // Create new wallet
        WalletDTO wallet = walletService.createWallet(userId);

        logger.info("Wallet created successfully for user {}", userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Wallet created successfully", "wallet", wallet));
    }

    /**
     * Retrieves the wallet details (monetary and units balances) for the currently authenticated user.
     *
     * @param authentication The Spring Security authentication object containing the user's principal (userId).
     * @return A ResponseEntity containing the user's WalletDTO, or a 404 Not Found if no wallet exists for the user.
     */
    @GetMapping("/getWallet")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getWallet(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("redirect", "/login"));
        }
        Long userId = (Long) authentication.getPrincipal();

        WalletDTO wallet = walletService.getWallet(userId);

        if (wallet == null) {
            logger.warn("Wallet not found for user {}", userId);
            return ResponseEntity.status(404).body(java.util.Map.of("error", "Wallet not found"));
        }

        logger.debug("Wallet retrieved for user {}", userId);
        return ResponseEntity.ok(wallet);
    }

    /**
     * Internal endpoint to atomically update the wallets of both the buyer and seller upon trade execution.
     * This endpoint bypasses standard user authentication but requires a shared internal secret
     * to prevent unauthorized public access.
     *
     * @param internalSecret The secret token provided in the 'X-Internal-Secret' header for internal authorization.
     * @param tradeDTO       The Data Transfer Object containing trade details (buyer ID, seller ID, quantity, price).
     * @return A ResponseEntity indicating successful update or unauthorized/server error.
     */
    @PostMapping("/internal/updateWallets")
    public ResponseEntity<?> updateWallets(@RequestHeader("X-Internal-Secret") String internalSecret, @RequestBody TradeDTO tradeDTO) {

        // Validate internal service secret
        try{
            if (!(internalSecretToken.equals(internalSecret))) {
                logger.warn("Unauthorized internal wallet update attempt");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized internal call");
            }

            logger.info("Updating wallets for trade between users {} and {}", tradeDTO.getBuyUserId(), tradeDTO.getSellUserId());
            walletService.updateWallets(tradeDTO);
            return ResponseEntity.ok(Map.of("message", "wallet updated successfully"));
        } catch (Exception e) {
            logger.error("Exception in updating wallets: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Exception in updating wallets: " + e.getMessage()));
        }
    }

    /**
     * Handles requests to add demo credits to the authenticated user's wallet.
     * Expected to be used when a user deposits funds via a payment gateway.
     *
     * @param req            The payload containing the specific monetary amount to be added. Validated for constraints (e.g., positive value).
     * @param authentication The Spring Security authentication object containing the user's principal (userId).
     * @return A ResponseEntity with a success message confirming the addition.
     */
    @PostMapping("/addMoney")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addMoney(@Valid @RequestBody AddMoneyRequest req, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("redirect", "/login"));
        }
        Long userId = (Long) authentication.getPrincipal();
        // Use walletService to add money
        walletService.addMoney(userId, req.getAmount());
        logger.info("Added money {} for user {}", req.getAmount(), userId);
        return ResponseEntity.ok(java.util.Map.of("message", "Money added successfully"));
    }

    /**
     * Handles requests to withdraw demo credits from the authenticated user's wallet.
     * Ensures the user has a sufficient balance before processing the withdrawal.
     *
     * @param req            The payload containing the monetary amount to be withdrawn.
     * @param authentication The Spring Security authentication object containing the user's principal (userId).
     * @return A ResponseEntity indicating a successful withdrawal or a 400 Bad Request if funds are insufficient.
     */
    @PostMapping("/withdrawMoney")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> withdrawMoney(@Valid @RequestBody WithdrawMoneyRequest req, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("redirect", "/login"));
        }
        Long userId = (Long) authentication.getPrincipal();
        // Use walletService to withdraw money
        try {
            walletService.withdrawMoney(userId, req.getAmount());
        } catch (IllegalArgumentException e) {
            logger.warn("Withdrawal failed for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", e.getMessage()));
        }
        logger.info("Withdrawn money {} for user {}", req.getAmount(), userId);
        return ResponseEntity.ok(java.util.Map.of("message", "Money withdrawn successfully"));
    }

    /**
     * Handles requests to add resource units to the authenticated user's wallet.
     * Typically used for direct deposits of units into the user's account.
     *
     * @param req            The payload indicating the exact quantity of units to add.
     * @param authentication The Spring Security authentication object containing the user's principal (userId).
     * @return A ResponseEntity with a success message confirming the addition.
     */
    @PostMapping("/addUnits")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addUnits(@Valid @RequestBody AddUnitsRequestDTO req, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("redirect", "/login"));
        }

        Long userId = (Long) authentication.getPrincipal();
        // Use walletService to add money
        walletService.addUnits(userId, req.getQuantity());
        logger.info("Added units {} for user {}", req.getQuantity(), userId);
        return ResponseEntity.ok(java.util.Map.of("message", "Units added successfully"));
    }

    /**
     * Handles requests to withdraw units from the authenticated user's wallet.
     * Ensures the user has an adequate units balance before completing the withdrawal.
     *
     * @param req            The payload indicating the exact quantity of units to be withdrawn.
     * @param authentication The Spring Security authentication object containing the user's principal (userId).
     * @return A ResponseEntity indicating a successful withdrawal or error if there is insufficient units.
     */
    @PostMapping("/withdrawUnits")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> withdrawUnits(@Valid @RequestBody WithdrawUnitsRequestDTO req, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("redirect", "/login"));
        }
        Long userId = (Long) authentication.getPrincipal();
        // Use walletService to add money
        walletService.withdrawUnits(userId, req.getQuantity());
        logger.info("Withdrawn units {} for user {}", req.getQuantity(), userId);
        return ResponseEntity.ok(java.util.Map.of("message", "Units withdrawn successfully"));
    }
}
