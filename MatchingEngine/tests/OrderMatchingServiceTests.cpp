#include <catch2/catch_test_macros.hpp>
#include "service/OrderMatchingService.hpp"
#include "core/OrderBook.hpp"

TEST_CASE("OrderMatchingService integration", "[service]")
{
    auto &book = core::OrderBook::getInstance();
    book.clear();

    SECTION("Process a buy order that matches existing sells")
    {
        // Place a sell order directly in the book
        dto::OrderData sellData{"sell1", "seller1", 10, 100.0, dto::Side::SELL, dto::OrderType::LIMIT};
        book.addOrder(core::Order(sellData));

        core::OrderMatchingService service;
        // Process a buy order that should match the sell
        dto::OrderData buyData{"buy1", "buyer1", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        REQUIRE_NOTHROW(service.handleOrder(buyData));

        // Verify the state: buy filled 5 at 100, sell partially filled (remaining 5)
        REQUIRE(book.getBestAsk() != nullptr);
        REQUIRE(book.getBestAsk()->quantity() == 5);
        REQUIRE(book.getLTP() == 100.0);
    }

    SECTION("Process a sell order that matches existing buys")
    {
        book.clear();
        dto::OrderData buyData{"buy1", "buyer1", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        book.addOrder(core::Order(buyData));

        core::OrderMatchingService service;
        dto::OrderData sellData{"sell1", "seller1", 5, 100.0, dto::Side::SELL, dto::OrderType::LIMIT};
        REQUIRE_NOTHROW(service.handleOrder(sellData));

        REQUIRE(book.getBestBid() != nullptr);
        REQUIRE(book.getBestBid()->quantity() == 5);
        REQUIRE(book.getLTP() == 100.0);
    }

    SECTION("No match when no opposite orders exist")
    {
        book.clear();

        core::OrderMatchingService service;
        dto::OrderData buyData{"buy1", "buyer1", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        REQUIRE_NOTHROW(service.handleOrder(buyData));

        // Buy order should be added to bids
        REQUIRE(book.getBestBid() != nullptr);
        REQUIRE(book.getBestBid()->order_id() == "buy1");
        REQUIRE(book.getBestBid()->quantity() == 5);
        service.handleOrder(buyData);
        REQUIRE(book.getBids().at(100.0).size() == 1);
    }

    SECTION("Multiple matches from one order")
    {
        book.clear();
        dto::OrderData sell1{"sell1", "seller1", 5, 95.0, dto::Side::SELL, dto::OrderType::LIMIT};
        dto::OrderData sell2{"sell2", "seller2", 5, 100.0, dto::Side::SELL, dto::OrderType::LIMIT};
        book.addOrder(core::Order(sell1));
        book.addOrder(core::Order(sell2));

        core::OrderMatchingService service;
        // Buy 12 at limit 100 -> matches sell1 (5@95), sell2 (5@100), remaining 2 added to bids
        dto::OrderData buyData{"buy1", "buyer1", 12, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        REQUIRE_NOTHROW(service.handleOrder(buyData));

        REQUIRE(book.getBestBid() != nullptr);
        REQUIRE(book.getBestBid()->quantity() == 2);
        // No asks should remain
        REQUIRE(book.getAsks().empty());
    }

    SECTION("Flow does not crash when Kafka producers are unavailable")
    {
        book.clear();
        // This test verifies that handleOrder does not throw even when
        // Kafka is not reachable. The producers should queue messages silently.
        core::OrderMatchingService service;
        dto::OrderData buyData{"buy1", "buyer1", 5, 0.0, dto::Side::BUY, dto::OrderType::MARKET};
        REQUIRE_NOTHROW(service.handleOrder(buyData));
    }
}
