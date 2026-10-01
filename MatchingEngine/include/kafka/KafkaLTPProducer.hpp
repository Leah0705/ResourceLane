#pragma once
#include "models/LTP.hpp"
#include <cppkafka/producer.h>

namespace kafka {

    /**
     * @class KafkaLTPProducer
     * @brief Producer for publishing Last Traded Price (LTP) events to Kafka.
     * 
     * Handles the serialization and transmission of LTP updates to the "ltp"
     * Kafka topic whenever a new trade occurs.
     */
    class KafkaLTPProducer {
    public:
        /**
         * @brief Default constructor, initializes the Kafka producer.
         */
        KafkaLTPProducer();

        /**
         * @brief Publishes an LTP object to the Kafka broker.
         * @param l The LTP object to publish.
         */
        void publish(const models::LTP& l);

private:
        bool deliverySuccessful_{false};
        cppkafka::Producer producer_;
};

} // namespace kafka
