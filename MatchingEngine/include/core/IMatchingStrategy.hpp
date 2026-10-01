#pragma once
#include <vector>
#include "models/Trade.hpp"

namespace core
{
    class Order;     // Forward declaration
    class OrderBook; // Forward declaration

    /**
     * @interface IMatchingStrategy
     * @brief Interface for matching strategies.
     *
     * Defines the contract for different order matching algorithms (e.g., Market, Limit).
     */
    class IMatchingStrategy
    {
    public:
        IMatchingStrategy() = default;
        virtual ~IMatchingStrategy();

        /**
         * @brief Matches an incoming order against the order book.
         * @param incoming The incoming order to match.
         * @param book The order book containing existing orders.
         * @return A vector of trades executed during the matching process.
         */
        virtual std::vector<models::Trade> match(Order &incoming, OrderBook &book) = 0;

    };

} // namespace core
