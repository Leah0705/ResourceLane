package io.resourcelane.trade_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.resourcelane.trade_service.dto.AuthCredentials;
import io.resourcelane.trade_service.dto.OrderDTO;
import io.resourcelane.trade_service.dto.OrderProducerDTO;
import io.resourcelane.trade_service.dto.OrderRequest;
import io.resourcelane.trade_service.dto.PastTradeDTO;
import io.resourcelane.trade_service.dto.StatusConsumerDTO;
import io.resourcelane.trade_service.dto.TradeConsumerDTO;
import io.resourcelane.trade_service.dto.WalletDTO;
import io.resourcelane.trade_service.model.Order;
import io.resourcelane.trade_service.model.OrderOutboxEvent;
import io.resourcelane.trade_service.model.OrderReservation;
import io.resourcelane.trade_service.model.ProcessedExecution;
import io.resourcelane.trade_service.model.Trade;
import io.resourcelane.trade_service.repository.OrderRepository;
import io.resourcelane.trade_service.repository.OrderOutboxRepository;
import io.resourcelane.trade_service.repository.OrderReservationRepository;
import io.resourcelane.trade_service.repository.ProcessedExecutionRepository;
import io.resourcelane.trade_service.repository.TradeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class for handling trade-related operations.
 * Manages orders, trades, wallet interactions, and status updates.
 */
@Service
public class TradeService {
    /** Logger for tracking trade service activities. */
    private static final Logger logger = LoggerFactory.getLogger(TradeService.class);

    /** Constant for buy side trade. */
    private static final String SIDE_BUY = "BUY";
    /** Constant for sell side trade. */
    private static final String SIDE_SELL = "SELL";
    /** Constant for market order type. */
    private static final String TYPE_MARKET = "MARKET";
    /** Constant for limit order type. */
    private static final String TYPE_LIMIT = "LIMIT";

    /** Repository for accessing and managing trade records. */
    private final TradeRepository tradeRepository;
    private final ProcessedExecutionRepository processedExecutionRepository;
    /** Repository for accessing and managing order records. */
    private final OrderRepository orderRepository;
    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderReservationRepository reservationRepository;
    private final JdbcTemplate jdbcTemplate;
    /** Template for sending messages to WebSocket clients. */
    private final SimpMessagingTemplate messagingTemplate;
    /** Template for executing HTTP requests to other services. */
    private final RestTemplate restTemplate;
    /** Mapper for converting between Java objects and JSON. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** The URL of the wallet service. */
    @Value("${wallet.service.url}")
    private String walletServiceUrl;

    /** The secret token used for internal service communication. */
    @Value("${internal.secret.token}")
    private String internalSecretToken;

    /**
     * Constructs a new TradeService with the specified dependencies.
     *
     * @param tradeRepository   Repository for trades.
     * @param orderRepository   Repository for orders.
     * @param messagingTemplate Template for WebSocket messaging.
     * @param restTemplate      Template for REST calls.
     */
    public TradeService(TradeRepository tradeRepository, OrderRepository orderRepository,
            ProcessedExecutionRepository processedExecutionRepository, OrderOutboxRepository orderOutboxRepository,
            OrderReservationRepository reservationRepository, JdbcTemplate jdbcTemplate,
            SimpMessagingTemplate messagingTemplate, RestTemplate restTemplate) {
        this.tradeRepository = tradeRepository;
        this.processedExecutionRepository = processedExecutionRepository;
        this.orderRepository = orderRepository;
        this.orderOutboxRepository = orderOutboxRepository;
        this.reservationRepository = reservationRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.messagingTemplate = messagingTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * Processes an order request, saves it, and sends it to the matching engine.
     *
     * @param orderRequest The order details.
     * @param userId       The ID of the user placing the order.
     * @throws Exception If an error occurs during processing.
     */
    @Transactional
    public void sendOrderToMatcher(OrderRequest orderRequest, Long userId) throws Exception {

        if (orderRequest.getQuantity() <= 0 || orderRequest.getPrice() == null
                || !Double.isFinite(orderRequest.getPrice()) || orderRequest.getPrice() <= 0
                || !(SIDE_BUY.equals(orderRequest.getSide()) || SIDE_SELL.equals(orderRequest.getSide()))
                || !(TYPE_LIMIT.equals(orderRequest.getType()) || TYPE_MARKET.equals(orderRequest.getType()))) {
            throw new IllegalArgumentException("Invalid order");
        }
        BigDecimal price;
        try {
            price = BigDecimal.valueOf(orderRequest.getPrice()).setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Price must have at most two decimal places", e);
        }
        // The wallet row is the shared serialization point for orders, withdrawals and settlements.
        Map<String, Object> wallet;
        try {
            wallet = jdbcTemplate.queryForMap(
                    "select balance, units from wallets where user_id = ? for update", userId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Wallet not found", e);
        }
        // The wallet lock serializes this check with the user's other submissions.
        List<Order> activeOrders = orderRepository.findByUserId(userId);
        if (activeOrders != null && activeOrders.stream()
                .anyMatch(existing -> wouldMatchOwnOrder(orderRequest, existing))) {
            throw new IllegalArgumentException("Order would match your own active order");
        }
        if (SIDE_BUY.equals(orderRequest.getSide())) {
            BigDecimal held = reservationRepository.findByUserIdAndSide(userId, SIDE_BUY).stream()
                    .map(r -> r.getPrice().multiply(BigDecimal.valueOf(r.heldQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal required = price.multiply(BigDecimal.valueOf(orderRequest.getQuantity()));
            if (((BigDecimal) wallet.get("balance")).subtract(held).compareTo(required) < 0) {
                throw new IllegalArgumentException("Insufficient available balance");
            }
        } else {
            int held = reservationRepository.findByUserIdAndSide(userId, SIDE_SELL).stream()
                    .mapToInt(OrderReservation::heldQuantity).sum();
            if (((Number) wallet.get("units")).doubleValue() - held < orderRequest.getQuantity()) {
                throw new IllegalArgumentException("Insufficient available units");
            }
        }

        // Convert OrderRequest to OrderProducerDTO for adding userId
        OrderProducerDTO orderProducerDTO = new OrderProducerDTO();
        BeanUtils.copyProperties(orderRequest, orderProducerDTO);
        orderProducerDTO.setUserId(userId.toString());

        // Save the order in the orders table
        Order orderEntity = new Order();
        orderEntity.setUserId(userId);
        orderEntity.setPrice(orderRequest.getPrice());
        orderEntity.setQuantity(orderRequest.getQuantity());
        orderEntity.setSide(orderRequest.getSide());
        orderEntity.setType(orderRequest.getType());

        // Copy the orderId from saved order into the orderProducerDTO and send it to the producer
        orderEntity = orderRepository.save(orderEntity);
        reservationRepository.save(new OrderReservation(orderEntity.getOrderId(), userId,
                orderRequest.getSide(), orderRequest.getType(), price, orderRequest.getQuantity()));
        orderProducerDTO.setOrderId(orderEntity.getOrderId().toString());

        String orderJson = objectMapper.writeValueAsString(orderProducerDTO);
        orderOutboxRepository.save(new OrderOutboxEvent(orderEntity.getOrderId(), orderJson));
        logger.info("Order {} queued for matching engine for user {}", orderEntity.getOrderId(), userId);
    }

    private static boolean wouldMatchOwnOrder(OrderRequest incoming, Order existing) {
        if (incoming.getSide().equals(existing.getSide())) {
            return false;
        }
        // A pending market order may still execute, so reject the opposing submission.
        if (TYPE_MARKET.equals(existing.getType())) {
            return true;
        }
        if (SIDE_SELL.equals(incoming.getSide()) && TYPE_MARKET.equals(incoming.getType())) {
            return true;
        }
        if (SIDE_BUY.equals(incoming.getSide())) {
            return existing.getPrice() <= incoming.getPrice();
        }
        return existing.getPrice() >= incoming.getPrice();
    }

    /**
     * Saves trade details for both buyer and seller.
     * Stores each execution separately so partial fills retain their own prices.
     *
     * @param tradeConsumerDTO The trade execution details.
     */
    @Transactional
    public void saveTrade(TradeConsumerDTO tradeConsumerDTO) {

        if (tradeConsumerDTO.getExecutionId() == null || tradeConsumerDTO.getExecutionId().isBlank()
                || tradeConsumerDTO.getExecutionId().length() > 128) {
            throw new IllegalArgumentException("Missing execution ID");
        }
        ProcessedExecution previous = processedExecutionRepository
                .findById(tradeConsumerDTO.getExecutionId()).orElse(null);
        if (previous != null) {
            if (!previous.matches(tradeConsumerDTO)) {
                throw new IllegalArgumentException("Execution ID reused with different trade details");
            }
            return;
        }

        try {

            // Buy side
            Trade tradeBuyerSide = new Trade();
            tradeBuyerSide.setUserId(Long.parseLong(tradeConsumerDTO.getBuyUserId()));
            tradeBuyerSide.setOrderId(Long.parseLong(tradeConsumerDTO.getBuyOrderId()));
            tradeBuyerSide.setPrice(tradeConsumerDTO.getPrice());
            tradeBuyerSide.setQuantity(tradeConsumerDTO.getQuantity());
            tradeBuyerSide.setSide(SIDE_BUY);

            tradeRepository.save(tradeBuyerSide);

            // Seller side
            Trade tradeSellerSide = new Trade();
            tradeSellerSide.setUserId(Long.parseLong(tradeConsumerDTO.getSellUserId()));
            tradeSellerSide.setOrderId(Long.parseLong(tradeConsumerDTO.getSellOrderId()));
            tradeSellerSide.setPrice(tradeConsumerDTO.getPrice());
            tradeSellerSide.setQuantity(tradeConsumerDTO.getQuantity());
            tradeSellerSide.setSide(SIDE_SELL);

            tradeRepository.save(tradeSellerSide);


            // Wallet update for both
            updateWallets(tradeConsumerDTO);
            processedExecutionRepository.saveAndFlush(new ProcessedExecution(tradeConsumerDTO));

        } catch (Exception e) {
            logger.error("Error saving trade: {}", e.getMessage(), e);
            throw new RuntimeException("Error saving trade: " + e.getMessage(), e);
        }
    }

    /**
     * Updates wallets for both parties involved in a trade.
     *
     * @param tradeConsumerDTO The trade details.
     */
    public void updateWallets(TradeConsumerDTO tradeConsumerDTO) {
        try {
            String url = walletServiceUrl + "internal/updateWallets";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Secret", internalSecretToken); // Use a secure token for internal calls
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<TradeConsumerDTO> requestEntity = new HttpEntity<>(tradeConsumerDTO, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Wallets updated successfully for trade involving orders {} and {}",
                    tradeConsumerDTO.getBuyOrderId(), tradeConsumerDTO.getSellOrderId());
            } else {
                logger.error("Wallet update failed with status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to update wallet");
            }
        } catch (Exception e) {
            logger.error("Error updating wallets: {}", e.getMessage(), e);
            throw new RuntimeException("Error updating wallets: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the history of executed trades for a user.
     *
     * @param userId The ID of the user.
     * @return A list of PastTradeDTOs.
     */
    public List<PastTradeDTO> pastTrades(Long userId) {
        // Fetch trades where user is involved
        List<Trade> userTrades = tradeRepository.findByUserId(userId);

        if (userTrades == null || userTrades.isEmpty()) {
            logger.debug("No past trades found for user {}", userId);
            return null;
        }

        List<PastTradeDTO> pastTrades = userTrades.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(trade -> {
                    PastTradeDTO pastTradeDTO = new PastTradeDTO();
                    pastTradeDTO.setTradeId(trade.getTradeId());
                    pastTradeDTO.setOrderId(trade.getOrderId());
                    pastTradeDTO.setUserId(trade.getUserId());
                    pastTradeDTO.setPrice(trade.getPrice());
                    pastTradeDTO.setQuantity(trade.getQuantity());
                    pastTradeDTO.setSide(trade.getSide());
                    pastTradeDTO.setCreatedAt(trade.getCreatedAt());
                    return pastTradeDTO;
                })
                .toList();

        return pastTrades;
    }

    /**
     * Fetches the wallet details for the authenticated user from the Wallet Service.
     *
     * @return The WalletDTO.
     */
    public WalletDTO getWallet() {
        WalletDTO walletDTO = null;

        try {
            String url = walletServiceUrl + "getWallet";
            AuthCredentials creds = (AuthCredentials) SecurityContextHolder.getContext().getAuthentication()
                    .getCredentials();
            String deviceFingerprint = creds.getFingerprint();
            String jwt = creds.getJwt();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Cookie", "jwt=" + jwt); // Send JWT in cookie format
            headers.set("X-Device-Fingerprint", deviceFingerprint);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<WalletDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    WalletDTO.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Wallet fetched successfully for user.");
                walletDTO = (WalletDTO) response.getBody();
            } else {
                logger.error("Wallet fetch failed with status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to fetch wallet");
            }
        } catch (Exception e) {
            logger.error("Failed to fetch wallet: {}", e.getMessage(), e);
        }
        return walletDTO;
    }

    /**
     * Checks if the user has sufficient funds or units in their wallet for the order.
     *
     * @param orderRequest The order request details.
     * @return True if wallet has sufficient funds/units, false otherwise.
     */
    public boolean checkWallet(OrderRequest orderRequest) {
        WalletDTO walletDTO = getWallet();
        if (walletDTO == null) {
            logger.warn("Wallet check failed: Wallet not found.");
            return false;
        }

        String side = orderRequest.getSide();
        String type = orderRequest.getType();

        if (SIDE_BUY.equals(side)) {
            if (TYPE_LIMIT.equals(type)) {
                // For limit buy: user pays price * quantity
                double cost = orderRequest.getPrice() * orderRequest.getQuantity();
                return walletDTO.getBalance() >= cost;
            } else if (TYPE_MARKET.equals(type)) {
                // The supplied price caps an immediate buy and its maximum exposure.
                return walletDTO.getBalance() >= orderRequest.getPrice() * orderRequest.getQuantity();
            }
        } else if (SIDE_SELL.equals(side)) {
            // For both limit and market sell, quantity of units must be available
            return walletDTO.getUnits() >= orderRequest.getQuantity();
        }

        return false;
    }

    /**
     * Sends a toast notification to the user via WebSocket.
     *
     * @param userId  The ID of the user.
     * @param message The message to send.
     */
    private void sendToast(Long userId, String message) {
        messagingTemplate.convertAndSend("/topic/toast/" + userId, message);
    }

    /**
     * Updates the status of an order based on messages from the matching engine.
     *
     * @param statusConsumerDTO The status update details.
     */
    @Transactional
    public void updateOrder(StatusConsumerDTO statusConsumerDTO) {
        try {
            // Fetch the order by orderId
            Long orderId = Long.parseLong(statusConsumerDTO.getOrderId());
            if (Boolean.TRUE.equals(statusConsumerDTO.getCompleted())) {
                Long statusUserId = Long.parseLong(statusConsumerDTO.getUserId());
                jdbcTemplate.queryForMap("select balance, units from wallets where user_id = ? for update",
                        statusUserId);
                OrderReservation reservation = reservationRepository.findById(orderId)
                        .orElseThrow(() -> new IllegalArgumentException("Reservation not found"));
                if (!reservation.getUserId().equals(statusUserId)
                        || !TYPE_MARKET.equals(reservation.getType())) {
                    throw new IllegalArgumentException("Invalid market completion owner or type");
                }
                if (statusConsumerDTO.getMatchedQuantity() == null) {
                    throw new IllegalArgumentException("Missing matched quantity");
                }
                int matched = statusConsumerDTO.getMatchedQuantity();
                if (matched < reservation.getSettledQuantity() || matched > reservation.getQuantity()
                        || reservation.getMatchedQuantity() != null
                        && reservation.getMatchedQuantity() != matched) {
                    throw new IllegalArgumentException("Invalid market completion");
                }
                reservation.setMatchedQuantity(matched);
                reservationRepository.save(reservation);
            }
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                logger.warn("Order not found for update: {}", orderId);
                return;
            }

            Long userId=Long.parseLong(statusConsumerDTO.getUserId());
            String type = order.getType();   // MARKET/TYPE
            if (statusConsumerDTO.getRemainingQuantity() != null) {
                int remaining = statusConsumerDTO.getRemainingQuantity();
                if (remaining < 0 || remaining > order.getQuantity()) {
                    // An older out-of-order absolute status cannot increase the open quantity.
                    if (remaining > order.getQuantity() && remaining <=
                            reservationRepository.findById(orderId).map(OrderReservation::getQuantity).orElse(0)) {
                        return;
                    }
                    throw new IllegalArgumentException("Invalid remaining quantity");
                }
                if (TYPE_MARKET.equals(type)) {
                    if (!Boolean.TRUE.equals(statusConsumerDTO.getCompleted())) {
                        throw new IllegalArgumentException("Market order requires completion");
                    }
                    orderRepository.delete(order);
                    sendToast(Long.parseLong(statusConsumerDTO.getUserId()),
                            "Market order completed: " + statusConsumerDTO.getMatchedQuantity() + " filled.");
                } else if (remaining == 0) {
                    orderRepository.delete(order);
                    sendToast(Long.parseLong(statusConsumerDTO.getUserId()), "Limit order fully filled.");
                } else if (remaining < order.getQuantity()) {
                    order.setQuantity(remaining);
                    orderRepository.save(order);
                    sendToast(Long.parseLong(statusConsumerDTO.getUserId()), "Limit order partially filled.");
                }
                return;
            }
            int quantity_order = order.getQuantity();
            int quantity_status = statusConsumerDTO.getQuantity();

            if (type.equals(TYPE_MARKET)) {
                if (quantity_status == 0) {
                    sendToast(userId, "Market order cancelled.");
                } else if (quantity_status == quantity_order) {
                    sendToast(userId, "Market order fully filled."+quantity_status);
                } else if (quantity_status < quantity_order) {
                    int q=quantity_order-quantity_status;
                    sendToast(userId, "Market order partially filled."+q);
                }
                orderRepository.delete(order);
            }

            if (type.equals(TYPE_LIMIT)) {
                if (quantity_status == quantity_order) {
                    orderRepository.delete(order);
                    sendToast(userId, "Limit order fully filled."+quantity_status);
                } else if (quantity_status < quantity_order) {
                    order.setQuantity(quantity_order - quantity_status);
                    orderRepository.save(order);
                    int q=quantity_order - quantity_status;
                    sendToast(userId, "Limit order partially filled."+q);
                } else {
                    throw new RuntimeException("Quantity status cannot be greater than order quantity");
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Error updating order: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves active orders for a user.
     *
     * @param userId The user ID.
     * @return A list of OrderDTOs.
     */
    public List<OrderDTO> getOrders(Long userId) {

        // Fetch orders for the user
        List<Order> orders = orderRepository.findByUserId(userId);

        if (orders == null || orders.isEmpty()) {
            logger.debug("No active orders found for user {}", userId);
            return null;
        }

        List<OrderDTO> orderDTOs = orders.stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(order -> {
                    OrderDTO orderDTO = new OrderDTO();
                    BeanUtils.copyProperties(order, orderDTO);
                    return orderDTO;
                })
                .toList();

        return orderDTOs;

    }

}
