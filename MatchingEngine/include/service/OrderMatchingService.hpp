#pragma once
#include "core/OrderBook.hpp"
#include "core/OrderFactory.hpp"
#include "kafka/KafkaTradeProducer.hpp"
#include "kafka/KafkaLTPProducer.hpp"
#include "kafka/KafkaOrderBookProducer.hpp"
#include "kafka/KafkaStatusProducer.hpp"
#include "service/OrderBookStateStore.hpp"
#include "service/RecentIdWindow.hpp"
#include <cstdint>
#include <memory>
#include <optional>
#include <string>

namespace core
{
    /** @brief Where a message sits in the order topic. */
    struct MessagePosition
    {
        int partition;
        std::int64_t offset;
    };

    /**
     * @class OrderMatchingService
     * @brief Applies order-topic messages to the book and publishes the results.
     *
     * Each applied message is saved together with its Kafka offset, so a message
     * redelivered after a restart is recognised and skipped.
     */
    class OrderMatchingService
    {
        OrderBook &book_ = OrderBook::getInstance(); // Reference to the singleton instance
        kafka::KafkaTradeProducer tradeProd_;
        kafka::KafkaLTPProducer ltpProd_;
        kafka::KafkaOrderBookProducer obProd_;
        kafka::KafkaStatusProducer statusProd_;
        RecentIdWindow recentIds_;
        PartitionOffsets offsets_;
        std::unique_ptr<OrderBookStateStore> stateStore_;

    public:
        /**
         * @brief Loads saved state when MATCHER_STATE_FILE is set.
         * MATCHER_DEDUP_WINDOW sets how many recent message IDs are kept (default 10000).
         */
        OrderMatchingService();

        /** @brief True when the saved state already includes the message at this position. */
        bool alreadyApplied(const MessagePosition &position) const;

        /** @brief Dispatches a parsed order-topic message. */
        void handle(const dto::OrderMessage &message, std::optional<MessagePosition> position = std::nullopt);

        /**
         * @brief Matches a new order and publishes trades, statuses, LTP and the book snapshot.
         * A republished order ID is ignored.
         */
        void handleOrder(const dto::OrderData &dto, std::optional<MessagePosition> position = std::nullopt);

        /**
         * @brief Removes the user's resting order and reports how much was cancelled.
         * Reports 0 when the order is no longer resting, for example because it already filled.
         */
        void handleCancel(const std::string &orderId, const std::string &userId,
                          std::optional<MessagePosition> position = std::nullopt);

    private:
        const Order *findResting(const std::string &orderId) const;
        void recordAndSave(std::optional<MessagePosition> position);
    };

} // namespace core
