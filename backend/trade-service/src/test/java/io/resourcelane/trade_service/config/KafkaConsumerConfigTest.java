package io.resourcelane.trade_service.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class KafkaConsumerConfigTest {

    private KafkaConsumerConfig config() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        return config;
    }

    private static Map<String, Object> properties(ConsumerFactory<String, String> factory) {
        assertThat(factory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        return ((DefaultKafkaConsumerFactory<String, String>) factory).getConfigurationProperties();
    }

    @Test
    void settlementConsumersShareOneGroupAndReadFromTheOldestMessage() {
        Map<String, Object> props = properties(config().consumerFactory());

        assertThat(props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo(KafkaConsumerConfig.SETTLEMENT_GROUP);
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        assertThat(props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
        assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
    }

    @Test
    void eachInstanceGetsItsOwnMarketDataGroupStartingFromTheNewestMessage() {
        KafkaConsumerConfig config = config();
        Object first = properties(config.marketDataConsumerFactory()).get(ConsumerConfig.GROUP_ID_CONFIG);
        Object second = properties(config.marketDataConsumerFactory()).get(ConsumerConfig.GROUP_ID_CONFIG);

        assertThat(first).asString().startsWith(KafkaConsumerConfig.MARKET_DATA_GROUP_PREFIX);
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isNotEqualTo(KafkaConsumerConfig.SETTLEMENT_GROUP);
        assertThat(properties(config.marketDataConsumerFactory()).get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG))
                .isEqualTo("latest");
    }

    @Test
    @SuppressWarnings("unchecked")
    void bothListenerFactoriesAreConfigured() {
        KafkaConsumerConfig config = config();
        ConcurrentKafkaListenerContainerFactory<String, String> settlement =
                config.kafkaListenerContainerFactory(mock(KafkaOperations.class));
        ConcurrentKafkaListenerContainerFactory<String, String> marketData = config.marketDataListenerFactory();

        assertThat(settlement.getConsumerFactory()).isNotNull();
        assertThat(marketData.getConsumerFactory()).isNotNull();
    }

    @Test
    void invalidSettlementMessageGoesToTheRecovererEvenWhenWrapped() {
        ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
        DefaultErrorHandler handler = KafkaConsumerConfig.settlementErrorHandler(recoverer);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade", 0, 5L, "resource", "{}");
        Exception thrown = new ListenerExecutionFailedException("listener failed",
                new RuntimeException("Failed to process trade message",
                        new RuntimeException("Error saving trade",
                                new IllegalArgumentException("Execution exceeds order reservation"))));

        handler.handleRemaining(thrown, List.of(record), mock(Consumer.class), mock(MessageListenerContainer.class));

        verify(recoverer).accept(eq(record), any());
    }

    @Test
    void walletValidationErrorGoesToTheRecoverer() {
        ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
        DefaultErrorHandler handler = KafkaConsumerConfig.settlementErrorHandler(recoverer);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade", 0, 6L, "resource", "{}");
        Exception thrown = new ListenerExecutionFailedException("listener failed",
                new RuntimeException("Error updating wallets", HttpClientErrorException.create(
                        HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable", HttpHeaders.EMPTY, new byte[0], null)));

        handler.handleRemaining(thrown, List.of(record), mock(Consumer.class), mock(MessageListenerContainer.class));

        verify(recoverer).accept(eq(record), any());
    }

    @Test
    void outageIsRetriedInsteadOfSkipped() {
        ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
        DefaultErrorHandler handler = KafkaConsumerConfig.settlementErrorHandler(recoverer);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("trade", 0, 7L, "resource", "{}");
        Exception thrown = new ListenerExecutionFailedException("listener failed",
                new RuntimeException("Error updating wallets", new ResourceAccessException("connection refused")));

        assertThatThrownBy(() -> handler.handleRemaining(thrown, List.of(record), mock(Consumer.class),
                mock(MessageListenerContainer.class)))
                .hasMessageContaining("Record in retry and not yet recovered");
        verify(recoverer, never()).accept(any(), any());
    }
}
