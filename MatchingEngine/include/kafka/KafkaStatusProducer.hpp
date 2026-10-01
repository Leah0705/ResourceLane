#pragma once
#include "models/Status.hpp"
#include <cppkafka/producer.h>

namespace kafka
{

    /**
     * @class KafkaStatusProducer
     * @brief Producer for publishing Order Status updates to Kafka.
     * 
     * Sends individual order updates (e.g. quantity filled) to the "status" topic,
     * providing real-time feedback to the users regarding their submitted orders.
     */
    class KafkaStatusProducer
    {
    public:
        /**
         * @brief Default constructor, initializes the Kafka producer.
         */
        KafkaStatusProducer();

        /**
         * @brief Publishes a Status object to the Kafka broker.
         * @param s The Status object to publish.
         */
        void publish(const models::Status &s);

    private:
        bool deliverySuccessful_{false};
        cppkafka::Producer producer_;
    };

} // namespace kafka
