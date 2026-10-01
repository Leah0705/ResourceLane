#pragma once
#include "core/OrderBook.hpp"
#include "core/OrderFactory.hpp"
#include "kafka/KafkaTradeProducer.hpp"
#include "kafka/KafkaLTPProducer.hpp"
#include "kafka/KafkaOrderBookProducer.hpp"
#include "kafka/KafkaStatusProducer.hpp"
#include "service/OrderBookStateStore.hpp"
#include <memory>
#include <unordered_set>

namespace kafka
{
    class KafkaOrderConsumer; // Forward declaration
}

namespace core
{

    /**
     * @class OrderMatchingService
     * @brief Service orchestrating order processing and matching.
     *
     * Handles incoming orders, delegates matching to the order book, and manages
     * Kafka producers for trades, LTP, and order book snapshots.
     */
    class OrderMatchingService
    {
        OrderBook &book_ = OrderBook::getInstance(); // Reference to the singleton instance
        kafka::KafkaTradeProducer tradeProd_;
        kafka::KafkaLTPProducer ltpProd_;
        kafka::KafkaOrderBookProducer obProd_;
        kafka::KafkaStatusProducer statusProd_;
        std::unordered_set<std::string> processedOrderIds_;
        std::unique_ptr<OrderBookStateStore> stateStore_;

    public:
        /**
         * @brief Constructs the OrderMatchingService.
         */
        OrderMatchingService(); // Explicitly declare the constructor

        /**
         * @brief Handles an incoming order.
         *
         * Creates an order object, matches it against the order book, publishes
         * resulting trades and updates to Kafka.
         *
         * @param dto The order data DTO.
         */
        void handleOrder(const dto::OrderData &dto);
    };

} // namespace core
