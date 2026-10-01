package io.resourcelane.trade_service.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigTest {

    @Test
    void consumerFactory_shouldCreateWithCorrectProperties() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

        ConsumerFactory<String, String> factory = config.consumerFactory();

        assertThat(factory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        DefaultKafkaConsumerFactory<String, String> defFactory = (DefaultKafkaConsumerFactory<String, String>) factory;

        Map<String, Object> props = defFactory.getConfigurationProperties();
        assertThat(props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(props.get(ConsumerConfig.GROUP_ID_CONFIG)).isEqualTo("MatchingEngine-group");
        assertThat(props.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)).isEqualTo("earliest");
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        assertThat(props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
        assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(StringDeserializer.class);
    }

    @Test
    void kafkaListenerContainerFactory_shouldCreateWithConsumerFactory() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");

        ConcurrentKafkaListenerContainerFactory<String, String> factory = config.kafkaListenerContainerFactory();

        assertThat(factory).isNotNull();
        assertThat(factory.getConsumerFactory()).isNotNull();
    }
}
