package io.resourcelane.trade_service.consumer;

import io.resourcelane.trade_service.dto.TradeConsumerDTO;
import io.resourcelane.trade_service.service.TradeService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Kafka Consumer for executed trades.
 */
@Component
public class TradeConsumer {
    /** Service to handle core trade processing logic. */
    private final TradeService tradeService;

    /** Mapper to deserialize JSON messages into Java objects. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Constructs the TradeConsumer.
     *
     * @param tradeService The trade service to handle trade saving.
     */
    public TradeConsumer(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    /**
     * Listens for trade events from Kafka and processes them.
     *
     * @param message The trade message in JSON format.
     */
    @KafkaListener(topics = "trade")
    public void listenTrade(String message) {
        try {
            TradeConsumerDTO tradeConsumerDTO = objectMapper.readValue(message, TradeConsumerDTO.class);
            tradeService.saveTrade(tradeConsumerDTO);
        } catch (Exception e) {
            // Log error
            throw new RuntimeException("Failed to process trade message", e);
        }
    }
}
