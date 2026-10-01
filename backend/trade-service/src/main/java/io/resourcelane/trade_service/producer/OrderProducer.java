package io.resourcelane.trade_service.producer;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for sending orders to the matching engine.
 */
@Component
public class OrderProducer {
    /** Logger for tracking order producer operations. */
    private static final Logger logger = LoggerFactory.getLogger(OrderProducer.class);

    /** Template for sending messages to Kafka topics. */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Constructs the OrderProducer.
     *
     * @param kafkaTemplate The Kafka template for sending messages.
     */
    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends an order to the "order" Kafka topic.
     *
     * @param orderJson The order details in JSON format.
     */
    public CompletableFuture<SendResult<String, String>> sendOrder(String orderJson) {

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send("order", orderJson);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("Failed to send order to Kafka: {}", ex.getMessage());
            } else {
                logger.info("Order sent to Kafka: {}", orderJson);
            }
        });
        return future;
    }
}
