#pragma once
#include "core/IMatchingStrategy.hpp"

namespace core
{

    class BuyMarketStrategy : public IMatchingStrategy
    {
    public:
        BuyMarketStrategy();
        std::vector<models::Trade> match(Order &incoming, OrderBook &book) override;
        ~BuyMarketStrategy();
    };

} // namespace core
