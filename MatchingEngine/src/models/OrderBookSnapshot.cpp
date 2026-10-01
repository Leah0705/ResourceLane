/**
 * @file OrderBookSnapshot.cpp
 * @brief Implementation of the OrderBookSnapshot class.
 */

#include "models/OrderBookSnapshot.hpp"

namespace models
{

    /**
     * @brief Constructs an OrderBookSnapshot.
     * 
     * @param bids The map of bid orders from the order book.
     * @param asks The map of ask orders from the order book.
     * @param ts The timestamp when the snapshot was taken.
     */
    OrderBookSnapshot::OrderBookSnapshot(
        const std::map<double, std::list<core::Order>, std::greater<>> &bids,
        const std::map<double, std::list<core::Order>> &asks,
        std::chrono::system_clock::time_point ts)
        : bids_(bids), asks_(asks), ts_(ts) {}

    /**
     * @brief Gets the timestamp of the snapshot.
     * @return The creation timestamp.
     */
    std::chrono::system_clock::time_point OrderBookSnapshot::timestamp() const
    {
        return ts_;
    }

    /**
     * @brief Aggregates the volume of bids at each price level.
     * 
     * Iterates over the bid map and computes the total quantity of all orders
     * resting at each specific price.
     * 
     * @return A map of price to total volume, sorted descending.
     */
    std::map<double, int, std::greater<>> OrderBookSnapshot::bidVolumes() const
    {
        std::map<double, int, std::greater<>> result;
        for (std::map<double, std::list<core::Order>, std::greater<>>::const_iterator it = bids_.begin(); it != bids_.end(); ++it)
        {
            int volume = 0;
            for (std::list<core::Order>::const_iterator orderIt = it->second.begin(); orderIt != it->second.end(); ++orderIt)
            {
                volume += orderIt->quantity();
            }
            result[it->first] = volume;
        }
        return result;
    }

    /**
     * @brief Aggregates the volume of asks at each price level.
     * 
     * Iterates over the ask map and computes the total quantity of all orders
     * resting at each specific price.
     * 
     * @return A map of price to total volume, sorted ascending.
     */
    std::map<double, int> OrderBookSnapshot::askVolumes() const
    {
        std::map<double, int> result;
        for (std::map<double, std::list<core::Order>>::const_iterator it = asks_.begin(); it != asks_.end(); ++it)
        {
            int volume = 0;
            for (std::list<core::Order>::const_iterator orderIt = it->second.begin(); orderIt != it->second.end(); ++orderIt)
            {
                volume += orderIt->quantity();
            }
            result[it->first] = volume;
        }
        return result;
    }

} // namespace models
