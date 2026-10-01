#include <catch2/catch_test_macros.hpp>
#include "models/LTP.hpp"
#include "models/Status.hpp"
#include "models/Trade.hpp"
#include "models/OrderBookSnapshot.hpp"
#include "core/Order.hpp"
#include "dto/OrderData.hpp"
#include <chrono>
#include <map>
#include <list>

TEST_CASE("LTP model", "[model]")
{
    auto now = std::chrono::system_clock::now();
    models::LTP ltp(150.25, now);
    REQUIRE(ltp.price() == 150.25);
    REQUIRE(ltp.timestamp() == now);
}

TEST_CASE("Status model", "[model]")
{
    auto now = std::chrono::system_clock::now();
    models::Status status("ord1", "usr1", "BUY", 10, now);
    REQUIRE(status.orderId() == "ord1");
    REQUIRE(status.userId() == "usr1");
    REQUIRE(status.side() == "BUY");
    REQUIRE(status.quantity() == 10);
    REQUIRE(status.timestamp() == now);
}

TEST_CASE("Trade model", "[model]")
{
    auto now = std::chrono::system_clock::now();
    models::Trade trade("buy1", "sell1", "buyUser", "sellUser", 200.0, 5, now);
    models::Trade another("buy1", "sell1", "buyUser", "sellUser", 200.0, 5, now);
    REQUIRE_FALSE(trade.executionId().empty());
    REQUIRE(trade.executionId() == another.executionId());
    REQUIRE(trade.executionId() == "buy1:sell1");
    REQUIRE(models::Trade(trade).executionId() == trade.executionId());
    REQUIRE(trade.buyOrderId() == "buy1");
    REQUIRE(trade.sellOrderId() == "sell1");
    REQUIRE(trade.buyUserId() == "buyUser");
    REQUIRE(trade.sellUserId() == "sellUser");
    REQUIRE(trade.price() == 200.0);
    REQUIRE(trade.quantity() == 5);
    REQUIRE(trade.timestamp() == now);
}

TEST_CASE("OrderBookSnapshot model", "[model]")
{
    auto now = std::chrono::system_clock::now();

    dto::OrderData d1{"b1", "u1", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
    dto::OrderData d2{"b2", "u2", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT};
    dto::OrderData d3{"s1", "u3", 7, 200.0, dto::Side::SELL, dto::OrderType::LIMIT};

    std::map<double, std::list<core::Order>, std::greater<>> bids;
    bids[100.0] = {core::Order(d1), core::Order(d2)};

    std::map<double, std::list<core::Order>> asks;
    asks[200.0] = {core::Order(d3)};

    models::OrderBookSnapshot snap(bids, asks, now);
    REQUIRE(snap.timestamp() == now);

    auto bidVols = snap.bidVolumes();
    REQUIRE(bidVols.size() == 1);
    REQUIRE(bidVols.count(100.0) == 1);
    REQUIRE(bidVols[100.0] == 15);

    auto askVols = snap.askVolumes();
    REQUIRE(askVols.size() == 1);
    REQUIRE(askVols.count(200.0) == 1);
    REQUIRE(askVols[200.0] == 7);
}

TEST_CASE("OrderBookSnapshot empty order book", "[model]")
{
    auto now = std::chrono::system_clock::now();
    std::map<double, std::list<core::Order>, std::greater<>> bids;
    std::map<double, std::list<core::Order>> asks;

    models::OrderBookSnapshot snap(bids, asks, now);
    REQUIRE(snap.bidVolumes().empty());
    REQUIRE(snap.askVolumes().empty());
}
