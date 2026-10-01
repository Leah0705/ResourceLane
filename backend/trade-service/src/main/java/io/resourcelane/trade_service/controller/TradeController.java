package io.resourcelane.trade_service.controller;

import io.resourcelane.trade_service.dto.CancelOrderRequest;
import io.resourcelane.trade_service.dto.OrderDTO;
import io.resourcelane.trade_service.dto.OrderRequest;
import io.resourcelane.trade_service.dto.PastTradeDTO;
import io.resourcelane.trade_service.service.TradeService;
import jakarta.validation.Valid;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST Controller for Trade services.
 * Handles order creation, and retrieval of past trades and active orders.
 */
@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("api/trade")
public class TradeController {

    /** Logger for tracking trade controller activities. */
    private static final Logger logger = LoggerFactory.getLogger(TradeController.class);

    /** Service for handling trade business logic. */
    private final TradeService tradeService;

    /**
     * Constructs a new TradeController with the specified TradeService.
     *
     * @param tradeService The service used for trade operations.
     */
    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    /**
     * Places a new order.
     *
     * @param orderRequest   The order details (price, quantity, side, type).
     * @param authentication The authentication object.
     * @return A response entity indicating success or failure.
     */
    @PostMapping("/createOrder")
    public ResponseEntity<?> placeOrder(@Valid @RequestBody OrderRequest orderRequest, Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401)
                        .body(java.util.Map.of("error", "Unauthorized: User not authenticated"));
            }
            Long userId = (Long) authentication.getPrincipal();

            tradeService.sendOrderToMatcher(orderRequest, userId);
            logger.info("Order queued for MatchingEngine for user {}", userId);
            return ResponseEntity.accepted().body(java.util.Map.of("status", "Order queued for MatchingEngine"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Exception while placing order for user {}: {}", authentication != null ? authentication.getPrincipal() : "unknown", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(java.util.Map.of("error", "Failed to send order exception: " + e.getMessage()));
        }
    }

    /**
     * Queues a cancel request for one of the user's resting limit orders.
     * The matcher's answer arrives later as a status update and a toast.
     *
     * @param request        The order to cancel.
     * @param authentication The current user's authentication.
     * @return 202 when queued, 404 for an unknown order, 409 when the order cannot be cancelled.
     */
    @PostMapping("/cancelOrder")
    public ResponseEntity<?> cancelOrder(@Valid @RequestBody CancelOrderRequest request, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of("error", "Unauthorized: User not authenticated"));
        }
        Long userId = (Long) authentication.getPrincipal();
        try {
            tradeService.requestCancel(request.getOrderId(), userId);
            return ResponseEntity.accepted().body(java.util.Map.of("status", "Cancel request queued"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(java.util.Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to cancel order {} for user {}: {}", request.getOrderId(), userId, e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Failed to cancel order"));
        }
    }

    /**
     * Retrieves past trades for the authenticated user.
     *
     * @param authentication The authentication object.
     * @return A list of past trades.
     */
    @GetMapping("/getPastTrades")
    public ResponseEntity<?> pastTrades(Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401)
                        .body(java.util.Map.of("error", "Unauthorized: User not authenticated"));
            }
            Long userId = (Long) authentication.getPrincipal();

            List<PastTradeDTO> pastTrades = tradeService.pastTrades(userId);
            if (pastTrades == null || pastTrades.isEmpty()) {
                logger.debug("No past trades found for user {}", userId);
                return ResponseEntity.ok(java.util.Map.of("message", "No past trades found"));
            }

            logger.debug("Returned {} past trades for user {}", pastTrades.size(), userId);
            return ResponseEntity.ok(java.util.Map.of("status", "Trades sent sucessfully", "pastTrades", pastTrades));
        } catch (Exception e) {
            logger.error("Failed to fetch past trades for user {}: {}", authentication != null ? authentication.getPrincipal() : "unknown", e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Failed to send trades"));
        }
    }

    /**
     * Retrieves active orders for the authenticated user.
     *
     * @param authentication The authentication object.
     * @return A list of active orders.
     */
    @GetMapping("/getOrders")
    public ResponseEntity<?> getOrders(Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401)
                        .body(java.util.Map.of("error", "Unauthorized: User not authenticated"));
            }
            Long userId = (Long) authentication.getPrincipal();

            List<OrderDTO> orders = tradeService.getOrders(userId);
            if (orders == null || orders.isEmpty()) {
                logger.debug("No active orders found for user {}", userId);
                return ResponseEntity.ok(java.util.Map.of("message", "No orders found"));
            }

            logger.debug("Returned {} active orders for user {}", orders.size(), userId);
            return ResponseEntity.ok(java.util.Map.of("status", "Orders sent successfully", "orders", orders));
        } catch (Exception e) {
            logger.error("Failed to fetch orders for user {}: {}", authentication != null ? authentication.getPrincipal() : "unknown", e.getMessage(), e);
            return ResponseEntity.status(500).body(java.util.Map.of("error", "Failed to send orders: " + e.getMessage()));
        }
    }

}
