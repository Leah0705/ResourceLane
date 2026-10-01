#pragma once
#include "models/OrderBookSnapshot.hpp"
#include <cppkafka/producer.h>

namespace kafka {

    /**
     * @class KafkaOrderBookProducer
     * @brief Producer for publishing OrderBook snapshots to Kafka.
     * 
     * Periodically or event-driven, this class serializes the entire state of 
     * the orderbook (bids and asks with aggregated volumes) and sends it to the 
     * "orderbook" topic.
     */
    class KafkaOrderBookProducer {
    public:
        /**
         * @brief Default constructor, initializes the Kafka producer.
         */
        KafkaOrderBookProducer();

        /**
         * @brief Publishes an OrderBookSnapshot object to the Kafka broker.
         * @param s The OrderBookSnapshot object to publish.
         */
        void publish(const models::OrderBookSnapshot& s);

private:
        bool deliverySuccessful_{false};
        cppkafka::Producer producer_;
};

} // namespace kafka
