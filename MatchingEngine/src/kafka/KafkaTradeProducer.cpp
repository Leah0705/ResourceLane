/**
 * @file KafkaTradeProducer.cpp
 * @brief Implementation of the KafkaTradeProducer class.
 */

#include "kafka/KafkaTradeProducer.hpp"
#include <iostream>
#include <nlohmann/json.hpp>
#include <cstdlib>
#include <stdexcept>

namespace kafka
{

    /**
     * @brief Default constructor for KafkaTradeProducer.
     * 
     * Initializes the underlying cppkafka::Producer by fetching the broker address
     * from the KAFKA_BROKER environment variable (default: 127.0.0.1:29092).
     */
    KafkaTradeProducer::KafkaTradeProducer()
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
     * @brief Serializes a Trade model into JSON and publishes it to the "trade" topic.
     * 
     * @param t The Trade object containing match details like IDs, price, quantity, and timestamp.
     */
    void KafkaTradeProducer::publish(const models::Trade &t)
    {
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(t.timestamp().time_since_epoch()).count();
        nlohmann::json j = {
            {"executionId", t.executionId()},
            {"buyOrderId", t.buyOrderId()},
            {"sellOrderId", t.sellOrderId()},
            {"buyUserId", t.buyUserId()},
            {"sellUserId", t.sellUserId()},
            {"price", t.price()},
            {"quantity", t.quantity()},
            {"timestamp", ms}};
        std::string payload = j.dump();
        deliverySuccessful_ = false;
        producer_.produce(cppkafka::MessageBuilder("trade").payload(payload));
        if (std::getenv("MATCHER_STATE_FILE")) {
            producer_.flush(std::chrono::seconds(10));
            if (!deliverySuccessful_) throw std::runtime_error("Trade delivery not confirmed");
        }
        std::cout << "Published Trade to 'trade': " << payload << std::endl;
    }

} // namespace kafka
