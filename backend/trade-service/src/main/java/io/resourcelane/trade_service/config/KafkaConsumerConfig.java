package io.resourcelane.trade_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer configuration.
 *
 * Settlement messages (trade, status) and market data (ltp, orderbook) need different
 * failure handling and different consumer groups, so each gets its own listener factory.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    /** Consumer group shared by every trade-service instance for settlement topics. */
    static final String SETTLEMENT_GROUP = "trade-service-settlement";
    /** Prefix of the per-instance consumer group for market data. */
    static final String MARKET_DATA_GROUP_PREFIX = "trade-service-marketdata-";
    /** Suffix of the topics that hold settlement messages which failed validation. */
    static final String DEAD_LETTER_SUFFIX = ".DLT";

    /** The Kafka bootstrap servers connection string. */
    @Value("${KAFKA_BROKER:kafka:9092}")
    private String bootstrapServers;

    /**
     * Settlement consumers share one group, so each trade and status message is applied by
     * exactly one instance, starting from the oldest unread message.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(SETTLEMENT_GROUP, "earliest"));
    }

    /**
     * Market data is pushed to the browsers connected to this instance, so every instance needs
     * every update. A group of its own gives it all partitions; it starts from the newest message
     * because only the latest price and book matter.
     */
    @Bean
    public ConsumerFactory<String, String> marketDataConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerProperties(MARKET_DATA_GROUP_PREFIX + UUID.randomUUID(), "latest"));
    }

    /**
     * Default factory, used by the settlement listeners.
     *
     * @param kafkaTemplate Template used to publish failed messages to the dead-letter topics.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            KafkaOperations<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(settlementErrorHandler(deadLetterRecoverer(kafkaTemplate)));
        return factory;
    }

    /** Factory for the ltp and orderbook listeners. A failed update is logged and skipped. */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> marketDataListenerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(marketDataConsumerFactory());
        // The next snapshot replaces this one, so retrying a stale update has no value.
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0L)));
        return factory;
    }

    /**
     * Sends a failed record to {@code <topic>.DLT}. Partition -1 lets Kafka choose, so the
     * dead-letter topic does not need as many partitions as the source topic.
     */
    static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaOperations<String, String> template) {
        return new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, -1));
    }

    /**
     * Wallet or database outages are retried until they recover, so no execution is skipped
     * while a dependency is down. A message that fails validation would fail on every attempt
     * and block the partition, so it goes to the recoverer instead.
     */
    static DefaultErrorHandler settlementErrorHandler(ConsumerRecordRecoverer recoverer) {
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        // Classification also inspects the cause chain, so wrapped exceptions are recognised.
        handler.addNotRetryableExceptions(IllegalArgumentException.class, JsonProcessingException.class,
                HttpClientErrorException.UnprocessableEntity.class);
        return handler;
    }

    private Map<String, Object> consumerProperties(String groupId, String offsetReset) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return props;
    }
}
