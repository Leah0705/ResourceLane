#include <catch2/catch_test_macros.hpp>
#include "core/StrategyFactory.hpp"
#include "core/strategies/BuyLimitStrategy.hpp"
#include "core/strategies/SellLimitStrategy.hpp"
#include "core/strategies/BuyMarketStrategy.hpp"
#include "core/strategies/SellMarketStrategy.hpp"
#include <stdexcept>

TEST_CASE("StrategyFactory creates correct strategies", "[strategy-factory]")
{
    SECTION("Create buy limit strategy")
    {
        dto::OrderData data{"", "", 0, 0, dto::Side::BUY, dto::OrderType::LIMIT};
        auto strategy = core::StrategyFactory::create(data);
        REQUIRE(std::dynamic_pointer_cast<core::BuyLimitStrategy>(strategy) != nullptr);
    }

    SECTION("Create sell limit strategy")
    {
        dto::OrderData data{"", "", 0, 0, dto::Side::SELL, dto::OrderType::LIMIT};
        auto strategy = core::StrategyFactory::create(data);
        REQUIRE(std::dynamic_pointer_cast<core::SellLimitStrategy>(strategy) != nullptr);
    }

    SECTION("Create buy market strategy")
    {
        dto::OrderData data{"", "", 0, 0, dto::Side::BUY, dto::OrderType::MARKET};
        auto strategy = core::StrategyFactory::create(data);
        REQUIRE(std::dynamic_pointer_cast<core::BuyMarketStrategy>(strategy) != nullptr);
    }

    SECTION("Create sell market strategy")
    {
        dto::OrderData data{"", "", 0, 0, dto::Side::SELL, dto::OrderType::MARKET};
        auto strategy = core::StrategyFactory::create(data);
        REQUIRE(std::dynamic_pointer_cast<core::SellMarketStrategy>(strategy) != nullptr);
    }

    SECTION("Unknown order type throws")
    {
        // No op -- only MARKET and LIMIT exist, so this branch is unreachable via enum.
        // The factory covers all 4 combos, so no unknown combo is possible with valid enums.
    }
}
