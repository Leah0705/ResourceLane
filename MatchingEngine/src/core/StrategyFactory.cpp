/**
 * @file StrategyFactory.cpp
 * @brief Implementation of the StrategyFactory class.
 */

#include "core/StrategyFactory.hpp"
#include "core/strategies/BuyLimitStrategy.hpp"
#include "core/strategies/SellLimitStrategy.hpp"
#include "core/strategies/BuyMarketStrategy.hpp"
#include "core/strategies/SellMarketStrategy.hpp"

#include <stdexcept>

namespace core
{

    /**
     * @brief Instantiates the appropriate matching strategy based on the order's type and side.
     * 
     * Evaluates the incoming OrderData DTO to decide whether a Buy Limit, Sell Limit,
     * Buy Market, or Sell Market strategy is needed. It creates and returns a shared 
     * pointer to the specific strategy instance.
     * 
     * @param dto The incoming order data containing type (Market/Limit) and side (Buy/Sell).
     * @return std::shared_ptr<IMatchingStrategy> A shared pointer to the concrete matching strategy.
     * @throws std::invalid_argument If the combination of type and side is not supported.
     */
    std::shared_ptr<IMatchingStrategy> StrategyFactory::create(const dto::OrderData &dto)
    {
        using namespace dto;

        if (dto.side == Side::BUY && dto.type == OrderType::LIMIT)
            return std::make_shared<BuyLimitStrategy>();
        else if (dto.side == Side::SELL && dto.type == OrderType::LIMIT)
            return std::make_shared<SellLimitStrategy>();
        else if (dto.side == Side::BUY && dto.type == OrderType::MARKET)
            return std::make_shared<BuyMarketStrategy>();
        else if (dto.side == Side::SELL && dto.type == OrderType::MARKET)
            return std::make_shared<SellMarketStrategy>();

        throw std::invalid_argument("Unsupported order type or side.");
    }

} // namespace core