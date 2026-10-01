#pragma once
#include "core/IMatchingStrategy.hpp"

namespace core
{

    /**
     * @class SellLimitStrategy
     * @brief Strategy for matching Sell Limit orders.
     */
    class SellLimitStrategy : public IMatchingStrategy
    {
    public:
        SellLimitStrategy();
        virtual ~SellLimitStrategy();

        /**
         * @brief Matches a Sell Limit order against the order book.
         * @param incoming The incoming Sell Limit order.
         * @param book The order book.
         * @return A vector of executed trades.
         */
        std::vector<models::Trade>
        match(Order &incoming, OrderBook &book) override;
    };

} // namespace core
