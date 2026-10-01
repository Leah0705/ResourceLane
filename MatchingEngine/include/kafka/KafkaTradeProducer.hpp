#pragma once
#include "models/Trade.hpp"
#include <cppkafka/producer.h>

namespace kafka {

    /**
     * @class KafkaTradeProducer
     * @brief Producer for publishing Trade events to Kafka.
     * 
     * Handles the serialization and transmission of matched trade information 
     * to the "trade" Kafka topic so other microservices (like data storage or API)
     * can consume them.
     */
    class KafkaTradeProducer {
    public:
        /**
         * @brief Default constructor, initializes the Kafka producer.
         */
        KafkaTradeProducer();

        /**
         * @brief Publishes a Trade object to the Kafka broker.
         * @param t The Trade object to publish.
         */
        void publish(const models::Trade& t);

private:
    bool deliverySuccessful_{false};
    cppkafka::Producer producer_;
};

} // namespace kafka
