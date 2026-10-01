#pragma once
#include "core/IMatchingStrategy.hpp"

namespace core
{

    /**
     * @class BuyLimitStrategy
     * @brief Strategy for matching Buy Limit orders.
     */
    class BuyLimitStrategy : public IMatchingStrategy
    {
    public:
        BuyLimitStrategy();
        virtual ~BuyLimitStrategy();

        /**
         * @brief Matches a Buy Limit order against the order book.
         * @param incoming The incoming Buy Limit order.
         * @param book The order book.
         * @return A vector of executed trades.
         */
        std::vector<models::Trade> match(Order &incoming, OrderBook &book) override;

        static std::vector<models::Trade> marketTrades(OrderBook &book);
    };

} // namespace core
