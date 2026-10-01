#pragma once
#include <cppkafka/consumer.h>
#include <memory>

namespace core
{
    class OrderMatchingService; // Forward declaration
}

namespace kafka
{

    /**
     * @class KafkaOrderConsumer
     * @brief Consumer for receiving incoming orders from Kafka.
     */
    class KafkaOrderConsumer
    {
    public:
        /**
         * @brief Starts the consumer loop to process incoming orders.
         * @param svc Reference to the OrderMatchingService to process received orders.
         */
        void start(core::OrderMatchingService &svc);

    private:
        std::unique_ptr<cppkafka::Consumer> consumer_;
    };

} // namespace kafka
