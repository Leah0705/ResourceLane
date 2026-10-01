/**
 * @file KafkaOrderBookProducer.cpp
 * @brief Implementation of the KafkaOrderBookProducer class.
 */

#include "kafka/KafkaOrderBookProducer.hpp"
#include <iostream>
#include <nlohmann/json.hpp>
#include <cstdlib>
#include <stdexcept>

namespace kafka
{

    /**
     * @brief Default constructor for KafkaOrderBookProducer.
     * 
     * Initializes the underlying cppkafka::Producer by fetching the broker address
     * from the KAFKA_BROKER environment variable (default: 127.0.0.1:29092).
     */
    KafkaOrderBookProducer::KafkaOrderBookProducer()
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
     * @brief Serializes an OrderBookSnapshot model into JSON and publishes it to the "orderbook" topic.
     * 
     * Processes both bids and asks into JSON arrays of {price, volume} objects.
     * 
     * @param s The OrderBookSnapshot containing aggregated bid and ask volumes.
     */
    void KafkaOrderBookProducer::publish(const models::OrderBookSnapshot &s)
    {
        long long ms = std::chrono::duration_cast<std::chrono::milliseconds>(s.timestamp().time_since_epoch()).count();
        nlohmann::json bids = nlohmann::json::array();
        std::map<double, int, std::greater<>> bid_vols = s.bidVolumes();
        for (std::map<double, int, std::greater<>>::const_iterator it = bid_vols.begin(); it != bid_vols.end(); ++it)
        {
            nlohmann::json entry;
            entry["price"] = it->first;
            entry["volume"] = it->second;
            bids.push_back(entry);
        }
        nlohmann::json asks = nlohmann::json::array();
        std::map<double, int> ask_vols = s.askVolumes();
        for (std::map<double, int>::const_iterator it = ask_vols.begin(); it != ask_vols.end(); ++it)
        {
            nlohmann::json entry;
            entry["price"] = it->first;
            entry["volume"] = it->second;
            asks.push_back(entry);
        }
        nlohmann::json j = {
            {"timestamp", ms},
            {"bids", bids},
            {"asks", asks}};
        std::string payload = j.dump();
        deliverySuccessful_ = false;
        producer_.produce(cppkafka::MessageBuilder("orderbook").payload(payload));
        if (std::getenv("MATCHER_STATE_FILE")) {
            producer_.flush(std::chrono::seconds(10));
            if (!deliverySuccessful_) throw std::runtime_error("Order book delivery not confirmed");
        }
        std::cout << "Published OrderBook Snapshot to 'orderbook': " << payload << std::endl;
    }

} // namespace kafka
