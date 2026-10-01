#include <catch2/catch_test_macros.hpp>
#include "core/Order.hpp"
#include "dto/OrderData.hpp"

TEST_CASE("Order construction", "[order]")
{
    SECTION("Buy limit order")
    {
        dto::OrderData data{"order1", "user1", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        core::Order order(data);
        REQUIRE(order.order_id() == "order1");
        REQUIRE(order.user_id() == "user1");
        REQUIRE(order.quantity() == 10);
        REQUIRE(order.price() == 100.0);
        REQUIRE(order.side() == dto::Side::BUY);
        REQUIRE(order.type() == dto::OrderType::LIMIT);
    }

    SECTION("Sell market order")
    {
        dto::OrderData data{"order2", "user2", 5, 0.0, dto::Side::SELL, dto::OrderType::MARKET};
        core::Order order(data);
        REQUIRE(order.order_id() == "order2");
        REQUIRE(order.user_id() == "user2");
        REQUIRE(order.quantity() == 5);
        REQUIRE(order.price() == 0.0);
        REQUIRE(order.side() == dto::Side::SELL);
        REQUIRE(order.type() == dto::OrderType::MARKET);
    }

    SECTION("setQuantity updates quantity")
    {
        dto::OrderData data{"order3", "user1", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        core::Order order(data);
        order.setQuantity(3);
        REQUIRE(order.quantity() == 3);
    }

    SECTION("Zero quantity order")
    {
        dto::OrderData data{"order4", "user1", 0, 50.0, dto::Side::SELL, dto::OrderType::LIMIT};
        core::Order order(data);
        REQUIRE(order.quantity() == 0);
    }

    SECTION("Negative price order")
    {
        dto::OrderData data{"order5", "user1", 10, -5.0, dto::Side::SELL, dto::OrderType::LIMIT};
        core::Order order(data);
        REQUIRE(order.price() == -5.0);
    }
}
