#include <catch2/catch_test_macros.hpp>
#include "core/OrderFactory.hpp"
#include <stdexcept>

TEST_CASE("OrderFactory create orders", "[factory]")
{
    SECTION("Create buy market order")
    {
        dto::OrderData data{"o1", "u1", 10, 0.0, dto::Side::BUY, dto::OrderType::MARKET};
        core::Order order = core::OrderFactory::create(data);
        REQUIRE(order.order_id() == "o1");
        REQUIRE(order.user_id() == "u1");
        REQUIRE(order.quantity() == 10);
        REQUIRE(order.side() == dto::Side::BUY);
        REQUIRE(order.type() == dto::OrderType::MARKET);
    }

    SECTION("Create sell limit order")
    {
        dto::OrderData data{"o2", "u2", 5, 150.0, dto::Side::SELL, dto::OrderType::LIMIT};
        core::Order order = core::OrderFactory::create(data);
        REQUIRE(order.order_id() == "o2");
        REQUIRE(order.side() == dto::Side::SELL);
        REQUIRE(order.type() == dto::OrderType::LIMIT);
        REQUIRE(order.price() == 150.0);
    }

    SECTION("Create order with all fields")
    {
        dto::OrderData data{"full", "user99", 100, 99.99, dto::Side::BUY, dto::OrderType::LIMIT};
        core::Order order = core::OrderFactory::create(data);
        REQUIRE(order.order_id() == "full");
        REQUIRE(order.user_id() == "user99");
        REQUIRE(order.quantity() == 100);
        REQUIRE(order.price() == 99.99);
        REQUIRE(order.side() == dto::Side::BUY);
        REQUIRE(order.type() == dto::OrderType::LIMIT);
    }
}
