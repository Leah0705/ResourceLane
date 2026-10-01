/**
 * @file KafkaOrderConsumer.cpp
 * @brief Implementation of the KafkaOrderConsumer class.
 */

#include "kafka/KafkaOrderConsumer.hpp"
#include <cppkafka/configuration.h>
#include <cppkafka/message.h>
#include <cppkafka/consumer.h>
#include <iostream>
#include <stdexcept>
#include "kafka/OrderMessageParser.hpp"
#include "service/OrderMatchingService.hpp"

namespace kafka
{

    /**
     * @brief Starts the Kafka consumer loop to receive and process orders.
     * 
     * Uses the KAFKA_BROKER environment variable (or defaults to 127.0.0.1:29092)
     * to connect to the Kafka cluster. It subscribes to the "order" topic, continuously
     * polls for new messages, parses the JSON payload into an OrderData DTO, and
     * forwards it to the OrderMatchingService.
     * 
     * @param svc Reference to the OrderMatchingService that will process the incoming orders.
     */
    void KafkaOrderConsumer::start(core::OrderMatchingService &svc)
    {

        // --- DOCKER BLOCK: Use KAFKA_BROKER env var (default to localhost if not set) ---
        const char *broker_env = std::getenv("KAFKA_BROKER");
        std::string broker = broker_env ? broker_env : "127.0.0.1:29092";
        std::cout << "Using Kafka broker: " << broker << std::endl;
        cppkafka::Configuration config = {
            {"metadata.broker.list", broker},
            {"group.id", "MatchingEngine-group"},
            {"auto.offset.reset", "earliest"},
            {"enable.auto.commit", "false"},
            {"enable.auto.offset.store", "false"}};

        // --- End of Kafka broker selection ---
        // Create the consumer with the configuration
        consumer_ = std::make_unique<cppkafka::Consumer>(config);
        consumer_->subscribe({"order"});
        std::cout << "KafkaOrderConsumer started, listening for messages..." << std::endl;
        while (true)
        {
            auto msg = consumer_->poll(std::chrono::seconds(1));
            if (!msg)
                continue;
            if (msg.get_error())
            {
                if (msg.is_eof())
                    continue;
                // Broker and network errors are infrastructure failures: crash and let the restart retry.
                throw std::runtime_error("Kafka order poll failed: " + msg.get_error().to_string());
            }

            const core::MessagePosition position{msg.get_partition(), msg.get_offset()};
            if (svc.alreadyApplied(position))
            {
                // Saved state already includes this message; only the offset commit was lost.
                consumer_->commit(msg);
                continue;
            }

            dto::OrderMessage order;
            try
            {
                const std::string payload = msg.get_payload();
                order = parseOrderMessage(payload);
            }
            catch (const std::invalid_argument &e)
            {
                // A bad message would fail the same way after every restart, so record it and move on.
                std::cerr << "Skipping invalid order message at partition " << position.partition
                          << " offset " << position.offset << ": " << e.what() << std::endl;
                consumer_->commit(msg);
                continue;
            }

            std::cout << "Received order message for order " << order.order.order_id << std::endl;
            svc.handle(order, position);
            consumer_->commit(msg);
        }
    }

} // namespace kafka
