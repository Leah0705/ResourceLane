#include <catch2/catch_test_macros.hpp>
#include "core/OrderBook.hpp"
#include "core/Order.hpp"
#include "dto/OrderData.hpp"

static void addOrder(const std::string &id, const std::string &user,
                     int qty, double price, dto::Side side, dto::OrderType type)
{
    dto::OrderData data{id, user, qty, price, side, type};
    core::Order order(data);
    core::OrderBook::getInstance().addOrder(order);
}

TEST_CASE("OrderBook singleton", "[orderbook]")
{
    auto &b1 = core::OrderBook::getInstance();
    auto &b2 = core::OrderBook::getInstance();
    REQUIRE(&b1 == &b2);
}

TEST_CASE("OrderBook add and query orders", "[orderbook]")
{
    auto &book = core::OrderBook::getInstance();
    book.clear();

    SECTION("Empty book returns null for best bid/ask")
    {
        REQUIRE(book.getBestBid() == nullptr);
        REQUIRE(book.getBestAsk() == nullptr);
        REQUIRE(book.getBids().empty());
        REQUIRE(book.getAsks().empty());
    }

    SECTION("Add single buy limit order")
    {
        addOrder("b1", "u1", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto *best = book.getBestBid();
        REQUIRE(best != nullptr);
        REQUIRE(best->order_id() == "b1");
        REQUIRE(best->price() == 100.0);
        REQUIRE(best->quantity() == 10);
    }

    SECTION("Add single sell limit order")
    {
        addOrder("s1", "u2", 5, 200.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto *best = book.getBestAsk();
        REQUIRE(best != nullptr);
        REQUIRE(best->order_id() == "s1");
        REQUIRE(best->price() == 200.0);
    }

    SECTION("Bids sorted highest price first")
    {
        addOrder("b_low", "u1", 5, 90.0, dto::Side::BUY, dto::OrderType::LIMIT);
        addOrder("b_high", "u2", 3, 110.0, dto::Side::BUY, dto::OrderType::LIMIT);
        addOrder("b_mid", "u3", 7, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);

        auto &bids = book.getBids();
        REQUIRE(bids.size() == 3);
        auto it = bids.begin();
        REQUIRE(it->first == 110.0);
        ++it;
        REQUIRE(it->first == 100.0);
        ++it;
        REQUIRE(it->first == 90.0);
    }

    SECTION("Asks sorted lowest price first")
    {
        addOrder("s_high", "u1", 5, 200.0, dto::Side::SELL, dto::OrderType::LIMIT);
        addOrder("s_low", "u2", 3, 180.0, dto::Side::SELL, dto::OrderType::LIMIT);

        auto &asks = book.getAsks();
        REQUIRE(asks.size() == 2);
        auto it = asks.begin();
        REQUIRE(it->first == 180.0);
        ++it;
        REQUIRE(it->first == 200.0);
    }

    SECTION("Add buy market order")
    {
        addOrder("b_mkt", "u1", 10, 0.0, dto::Side::BUY, dto::OrderType::MARKET);
        auto *best = book.getBestBid();
        REQUIRE(best != nullptr);
        REQUIRE(best->order_id() == "b_mkt");
    }

    SECTION("Add sell market order")
    {
        addOrder("s_mkt", "u1", 10, 0.0, dto::Side::SELL, dto::OrderType::MARKET);
        auto *best = book.getBestAsk();
        REQUIRE(best != nullptr);
        REQUIRE(best->order_id() == "s_mkt");
    }

    SECTION("LTP get/set")
    {
        REQUIRE(book.getLTP() == 0.0);
        book.updateLTP(150.0);
        REQUIRE(book.getLTP() == 150.0);
        book.updateLTP(200.0);
        REQUIRE(book.getLTP() == 200.0);
    }

    SECTION("Remove buy order")
    {
        dto::OrderData data{"rem1", "u1", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        core::Order order(data);
        book.addOrder(order);
        REQUIRE(book.getBestBid() != nullptr);
        book.removeOrder(order);
        REQUIRE(book.getBestBid() == nullptr);
    }

    SECTION("Remove non-existent order")
    {
        dto::OrderData data{"nonexist", "u1", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
        core::Order order(data);
        REQUIRE_NOTHROW(book.removeOrder(order));
    }

    SECTION("Clear empty book")
    {
        book.clear();
        REQUIRE(book.getBids().empty());
        REQUIRE(book.getAsks().empty());
        REQUIRE(book.getLTP() == 0.0);
    }
}
