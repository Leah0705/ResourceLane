#pragma once

#include "core/IMatchingStrategy.hpp"
#include "dto/OrderData.hpp"
#include <memory>

namespace core
{

    /**
     * @class StrategyFactory
     * @brief Factory class for creating matching strategies.
     */
    class StrategyFactory
    {
    public:
        /**
         * @brief Creates a matching strategy based on the order type and side.
         * @param dto The order data containing type and side information.
         * @return A shared pointer to the created IMatchingStrategy.
         */
        static std::shared_ptr<IMatchingStrategy> create(const dto::OrderData &dto);
    };

} // namespace core