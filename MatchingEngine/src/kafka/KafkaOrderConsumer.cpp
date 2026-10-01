/**
 * @file KafkaOrderConsumer.cpp
 * @brief Implementation of the KafkaOrderConsumer class.
 */

#include "kafka/KafkaOrderConsumer.hpp"
#include <cppkafka/configuration.h>
#include <cppkafka/message.h>
#include <cppkafka/consumer.h>
#include <nlohmann/json.hpp>
#include <iostream>
#include <cmath>
#include <stdexcept>
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
            if (msg && !msg.get_error())
            {
                    std::string payload = msg.get_payload(); // Get as string
                    if (payload.empty()) throw std::runtime_error("Empty order message");
                        nlohmann::json j = nlohmann::json::parse(payload);
                        std::string order_id = j.at("orderId").get<std::string>();
                        std::string user_id = j.at("userId").get<std::string>();
                        int quantity = j.at("quantity").get<int>();
                        double price = j.at("price").get<double>();
                        std::string side = j.at("side").get<std::string>();
                        std::string type = j.at("type").get<std::string>();
                        if (order_id.empty() || user_id.empty() || quantity <= 0
                                || !std::isfinite(price) || price <= 0)
                            throw std::runtime_error("Invalid order message");
                        dto::Side parsedSide;
                        if (side == "BUY") parsedSide = dto::Side::BUY;
                        else if (side == "SELL") parsedSide = dto::Side::SELL;
                        else throw std::runtime_error("Invalid side: " + side);

                        dto::OrderType parsedType;
                        if (type == "MARKET") parsedType = dto::OrderType::MARKET;
                        else if (type == "LIMIT") parsedType = dto::OrderType::LIMIT;
                        else throw std::runtime_error("Invalid order type: " + type);

                        dto::OrderData orderData = {
                            order_id,
                            user_id,
                            quantity,
                            price,
                            parsedSide,
                            parsedType};
                        std::cout << "Received order: " << user_id << std::endl;
                        svc.handleOrder(orderData);
                        consumer_->commit(msg);
            }
            else if (msg && msg.get_error())
                throw std::runtime_error("Kafka order poll failed: " + msg.get_error().to_string());
        }
    }

} // namespace kafka
