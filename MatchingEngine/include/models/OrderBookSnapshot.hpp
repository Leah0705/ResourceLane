#pragma once
#include <map>
#include <list>
#include <chrono>
#include "core/Order.hpp"

namespace models
{

    /**
     * @class OrderBookSnapshot
     * @brief Represents a snapshot of the order book state at a point in time.
     */
    class OrderBookSnapshot
    {
    private:
        std::map<double, std::list<core::Order>, std::greater<>> bids_;
        std::map<double, std::list<core::Order>> asks_;
        std::chrono::system_clock::time_point ts_;

    public:
        /**
         * @brief Constructs an OrderBookSnapshot.
         * @param bids Current buy orders.
         * @param asks Current sell orders.
         * @param ts Timestamp of the snapshot.
         */
        OrderBookSnapshot(const std::map<double, std::list<core::Order>, std::greater<>> &bids,
                          const std::map<double, std::list<core::Order>> &asks,
                          std::chrono::system_clock::time_point ts);

        /**
         * @brief Gets the timestamp of the snapshot.
         * @return The time the snapshot was taken.
         */
        std::chrono::system_clock::time_point timestamp() const;

        /**
         * @brief Aggregates bid volumes by price.
         * @return A map of price to total volume for bids.
         */
        std::map<double, int, std::greater<>> bidVolumes() const;

        /**
         * @brief Aggregates ask volumes by price.
         * @return A map of price to total volume for asks.
         */
        std::map<double, int> askVolumes() const;
    };

} // namespace models
