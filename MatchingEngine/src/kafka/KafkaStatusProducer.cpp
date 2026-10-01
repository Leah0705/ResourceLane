/**
 * @file KafkaStatusProducer.cpp
 * @brief Implementation of the KafkaStatusProducer class.
 */

#include "kafka/KafkaStatusProducer.hpp"
#include <iostream>
#include <nlohmann/json.hpp>
#include <cstdlib>
#include <stdexcept>

namespace kafka
{

    /**
     * @brief Default constructor for KafkaStatusProducer.
     * 
     * Initializes the underlying cppkafka::Producer by fetching the broker address
     * from the KAFKA_BROKER environment variable (default: 127.0.0.1:29092).
     */
    KafkaStatusProducer::KafkaStatusProducer()
        : producer_([this] {
            const char* broker_env = std::getenv("KAFKA_BROKER");
            std::string broker = broker_env ? broker_env : "127.0.0.1:29092";
            cppkafka::Configuration config{{"metadata.broker.list", broker},
                                           {"acks", "all"}, {"enable.idempotence", "true"}};
            config.set_delivery_report_callback([this](cppkafka::Producer&, const cppkafka::Message& message) {
                deliverySuccessful_ = !message.get_error();
            });
            return config;
        }())
    {
    }

    /**
     * @brief Serializes a Status model into JSON and publishes it to the "status" topic.
     * 
     * @param s The Status object containing the updated order details (quantity filled, etc.).
     */
    void KafkaStatusProducer::publish(const models::Status &s)
    {
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(s.timestamp().time_since_epoch()).count();
        nlohmann::json j = {
            {"orderId", s.orderId()},
            {"userId", s.userId()},
            {"side", s.side()},
            {"quantity", s.quantity()},
            {"timestamp", ms}};
        if (s.remainingQuantity() >= 0)
        {
            j["remainingQuantity"] = s.remainingQuantity();
            j["matchedQuantity"] = s.matchedQuantity();
            j["completed"] = s.completed();
        }
        std::string payload = j.dump();
        deliverySuccessful_ = false;
        producer_.produce(cppkafka::MessageBuilder("status").payload(payload));
        if (std::getenv("MATCHER_STATE_FILE")) {
            producer_.flush(std::chrono::seconds(10));
            if (!deliverySuccessful_) throw std::runtime_error("Status delivery not confirmed");
        }
        std::cout << "Published Status to 'status': " << payload << std::endl;
    }

} // namespace kafka
