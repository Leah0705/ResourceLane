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

    /**
     * Every order and cancel shares one key. Kafka keeps records with the same key on one
     * partition, so the matcher reads them in the order the outbox sent them.
     */
    static final String BOOK_KEY = "resource";

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
     * Sends an order or cancel message to the "order" Kafka topic.
     *
     * @param orderJson The message in JSON format.
     */
    public CompletableFuture<SendResult<String, String>> sendOrder(String orderJson) {

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send("order", BOOK_KEY, orderJson);
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
