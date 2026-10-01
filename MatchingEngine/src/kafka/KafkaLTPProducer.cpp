/**
 * @file KafkaLTPProducer.cpp
 * @brief Implementation of the KafkaLTPProducer class.
 */

#include "kafka/KafkaLTPProducer.hpp"
#include <iostream>
#include <nlohmann/json.hpp>
#include <cstdlib>
#include <stdexcept>

namespace kafka
{
    namespace
    {
        const std::string kBookKey{"resource"};
    }


    /**
     * @brief Default constructor for KafkaLTPProducer.
     * 
     * Initializes the underlying cppkafka::Producer by fetching the broker address
     * from the KAFKA_BROKER environment variable (default: 127.0.0.1:29092).
     */
    KafkaLTPProducer::KafkaLTPProducer()
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
     * @brief Serializes an LTP model into JSON and publishes it to the "ltp" topic.
     * 
     * @param l The LTP object containing the latest price and timestamp.
     */
    void KafkaLTPProducer::publish(const models::LTP &l)
    {
        auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(l.timestamp().time_since_epoch()).count();
        nlohmann::json j = {
            {"price", l.price()},
            {"timestamp", ms}};
        std::string payload = j.dump();
        deliverySuccessful_ = false;
        // One book, one key: consumers see these events in the order the matcher produced them.
        producer_.produce(cppkafka::MessageBuilder("ltp").key(kBookKey).payload(payload));
        if (std::getenv("MATCHER_STATE_FILE")) {
            producer_.flush(std::chrono::seconds(10));
            if (!deliverySuccessful_) throw std::runtime_error("LTP delivery not confirmed");
        }
        std::cout << "Published LTP to 'ltp': " << payload << std::endl;
    }

} // namespace kafka
