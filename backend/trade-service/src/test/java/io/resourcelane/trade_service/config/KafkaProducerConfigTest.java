package io.resourcelane.trade_service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KafkaProducerConfigTest {

    @Test
    void producerFactory_shouldCreateWithCorrectProperties() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "kafka:9092");

        ProducerFactory<String, String> factory = config.producerFactory();

        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        DefaultKafkaProducerFactory<String, String> defFactory = (DefaultKafkaProducerFactory<String, String>) factory;

        Map<String, Object> props = defFactory.getConfigurationProperties();
        assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("kafka:9092");
        assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(props.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(props.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)).isEqualTo(true);
    }

    @Test
    void kafkaTemplate_shouldCreateWithProducerFactory() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "kafka:9092");

        KafkaTemplate<String, String> template = config.kafkaTemplate();

        assertThat(template).isNotNull();
    }
}
