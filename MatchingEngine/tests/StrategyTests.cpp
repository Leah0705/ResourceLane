#include <catch2/catch_test_macros.hpp>
#include "core/OrderBook.hpp"
#include "core/Order.hpp"
#include "core/strategies/BuyLimitStrategy.hpp"
#include "core/strategies/SellLimitStrategy.hpp"
#include "core/strategies/BuyMarketStrategy.hpp"
#include "core/strategies/SellMarketStrategy.hpp"

static void addOrder(const std::string &id, const std::string &user,
                     int qty, double price, dto::Side side, dto::OrderType type)
{
    dto::OrderData data{id, user, qty, price, side, type};
    core::OrderBook::getInstance().addOrder(core::Order(data));
}

static core::Order makeOrder(const std::string &id, const std::string &user,
                              int qty, double price, dto::Side side, dto::OrderType type)
{
    dto::OrderData data{id, user, qty, price, side, type};
    return core::Order(data);
}

// --- BuyLimitStrategy ---

TEST_CASE("BuyLimitStrategy match", "[strategy][buy-limit]")
{
    auto &book = core::OrderBook::getInstance();
    book.clear();

    SECTION("Limit buy matches when price >= lowest ask")
    {
        addOrder("ask1", "seller", 10, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);

        core::BuyLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].buyOrderId() == "buy1");
        REQUIRE(trades[0].sellOrderId() == "ask1");
        REQUIRE(trades[0].price() == 100.0);
        REQUIRE(trades[0].quantity() == 5);
        // Incoming fully filled, ask partially filled (remaining 5)
        REQUIRE(book.getBestAsk() != nullptr);
    }

    SECTION("Limit buy partial fill")
    {
        addOrder("ask1", "seller", 3, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);

        core::BuyLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].quantity() == 3);
        // Ask fully consumed, incoming has 7 remaining -> added to bids
        REQUIRE(book.getBestAsk() == nullptr);
        REQUIRE(book.getBestBid() != nullptr);
        REQUIRE(book.getBestBid()->order_id() == "buy1");
        REQUIRE(book.getBestBid()->quantity() == 7);
    }

    SECTION("Limit buy no match when price too low")
    {
        addOrder("ask1", "seller", 10, 105.0, dto::Side::SELL, dto::OrderType::LIMIT);
        // incoming price 100 < ask price 105 -> no match
        auto incoming = makeOrder("buy1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);

        core::BuyLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.empty());
        // Incoming added to bids with full quantity
        REQUIRE(book.getBestBid() != nullptr);
        REQUIRE(book.getBestBid()->quantity() == 5);
        // Original ask still there
        REQUIRE(book.getBestAsk() != nullptr);
    }

    SECTION("Limit buy matches multiple asks at different prices")
    {
        addOrder("ask_low", "seller", 5, 95.0, dto::Side::SELL, dto::OrderType::LIMIT);
        addOrder("ask_mid", "seller", 5, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);
        addOrder("ask_high", "seller", 5, 105.0, dto::Side::SELL, dto::OrderType::LIMIT);
        // incoming: buy 12 at limit 100 -> matches ask_low (5), ask_mid (5), then price 105 > 100, stops
        auto incoming = makeOrder("buy1", "buyer", 12, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);

        core::BuyLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 2);
        REQUIRE(trades[0].quantity() == 5);
        REQUIRE(trades[0].price() == 95.0);
        REQUIRE(trades[1].quantity() == 5);
        REQUIRE(trades[1].price() == 100.0);
        // incoming remaining 2 -> added to bids
        REQUIRE(book.getBestBid()->quantity() == 2);
        // ask_high (105) still in book since not matched
        REQUIRE(book.getBestAsk() != nullptr);
        REQUIRE(book.getBestAsk()->price() == 105.0);
    }

    SECTION("Limit buy LTP updates on match")
    {
        addOrder("ask1", "seller", 5, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);

        core::BuyLimitStrategy strategy;
        strategy.match(incoming, book);

        REQUIRE(book.getLTP() == 100.0);
    }
}

// --- SellLimitStrategy ---

TEST_CASE("SellLimitStrategy match", "[strategy][sell-limit]")
{
    auto &book = core::OrderBook::getInstance();
    book.clear();

    SECTION("Limit sell matches when price <= highest bid")
    {
        addOrder("bid1", "buyer", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto incoming = makeOrder("sell1", "seller", 5, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);

        core::SellLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].buyOrderId() == "bid1");
        REQUIRE(trades[0].sellOrderId() == "sell1");
        REQUIRE(trades[0].buyUserId() == "buyer");
        REQUIRE(trades[0].sellUserId() == "seller");
        REQUIRE(trades[0].price() == 100.0);
        REQUIRE(trades[0].quantity() == 5);
    }

    SECTION("Limit sell no match when price too high")
    {
        addOrder("bid1", "buyer", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        // incoming sell price 105 > highest bid 100 -> no match
        auto incoming = makeOrder("sell1", "seller", 5, 105.0, dto::Side::SELL, dto::OrderType::LIMIT);

        core::SellLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.empty());
        // Incoming added to asks
        REQUIRE(book.getBestAsk() != nullptr);
        REQUIRE(book.getBestAsk()->quantity() == 5);
    }

    SECTION("Limit sell partial fill")
    {
        addOrder("bid1", "buyer", 3, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto incoming = makeOrder("sell1", "seller", 10, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);

        core::SellLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].quantity() == 3);
        // Incoming remaining 7 -> added to asks
        REQUIRE(book.getBestAsk() != nullptr);
        REQUIRE(book.getBestAsk()->quantity() == 7);
    }

    SECTION("Limit sell matches multiple bids at different prices")
    {
        addOrder("bid_high", "buyer", 5, 105.0, dto::Side::BUY, dto::OrderType::LIMIT);
        addOrder("bid_mid", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        addOrder("bid_low", "buyer", 5, 95.0, dto::Side::BUY, dto::OrderType::LIMIT);
        // incoming: sell 12 at limit 100 -> matches bid_high (105 >= 100), bid_mid (100 >= 100), then bid_low (95 < 100), stops
        auto incoming = makeOrder("sell1", "seller", 12, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);

        core::SellLimitStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 2);
        REQUIRE(trades[0].quantity() == 5);
        REQUIRE(trades[0].price() == 105.0);
        REQUIRE(trades[1].quantity() == 5);
        REQUIRE(trades[1].price() == 100.0);
        // Incoming remaining 2 -> added to asks
        REQUIRE(book.getBestAsk()->quantity() == 2);
        // bid_low (95) still there
        REQUIRE(book.getBestBid()->price() == 95.0);
    }

    SECTION("Limit sell LTP updates on match")
    {
        addOrder("bid1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto incoming = makeOrder("sell1", "seller", 5, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);

        core::SellLimitStrategy strategy;
        strategy.match(incoming, book);

        REQUIRE(book.getLTP() == 100.0);
    }
}

// --- BuyMarketStrategy ---

TEST_CASE("BuyMarketStrategy match", "[strategy][buy-market]")
{
    auto &book = core::OrderBook::getInstance();
    book.clear();

    SECTION("Buy market matches immediately at best ask")
    {
        addOrder("ask1", "seller", 10, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::MARKET);

        core::BuyMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].price() == 100.0);
        REQUIRE(trades[0].quantity() == 5);
        // Ask partially filled (remaining 5)
        REQUIRE(book.getBestAsk() != nullptr);
        REQUIRE(book.getBestAsk()->quantity() == 5);
    }

    SECTION("Buy market consumes multiple asks if needed")
    {
        addOrder("ask_low", "seller", 5, 95.0, dto::Side::SELL, dto::OrderType::LIMIT);
        addOrder("ask_high", "seller", 5, 105.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 8, 105.0, dto::Side::BUY, dto::OrderType::MARKET);

        core::BuyMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 2);
        REQUIRE(trades[0].quantity() == 5);
        REQUIRE(trades[0].price() == 95.0);
        REQUIRE(trades[1].quantity() == 3);
        REQUIRE(trades[1].price() == 105.0);
        // ask_high partially filled (remaining 2)
        REQUIRE(book.getBestAsk() != nullptr);
        REQUIRE(book.getBestAsk()->quantity() == 2);
    }

    SECTION("Buy market partial fill if insufficient ask volume")
    {
        addOrder("ask1", "seller", 3, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 10, 100.0, dto::Side::BUY, dto::OrderType::MARKET);

        core::BuyMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].quantity() == 3);
        // No more asks - market order just executes what it can
        REQUIRE(book.getBestAsk() == nullptr);
    }

    SECTION("Buy market no asks available")
    {
        auto incoming = makeOrder("buy1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::MARKET);

        core::BuyMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.empty());
    }

    SECTION("Buy market updates LTP")
    {
        addOrder("ask1", "seller", 5, 100.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::MARKET);

        core::BuyMarketStrategy strategy;
        strategy.match(incoming, book);

        REQUIRE(book.getLTP() == 100.0);
    }

    SECTION("Buy market stops at the submitted price cap")
    {
        addOrder("ask_low", "seller", 2, 95.0, dto::Side::SELL, dto::OrderType::LIMIT);
        addOrder("ask_high", "seller", 3, 105.0, dto::Side::SELL, dto::OrderType::LIMIT);
        auto incoming = makeOrder("buy1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::MARKET);

        core::BuyMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].quantity() == 2);
        REQUIRE(book.getBestAsk() != nullptr);
        REQUIRE(book.getBestAsk()->price() == 105.0);
    }
}

// --- SellMarketStrategy ---

TEST_CASE("SellMarketStrategy match", "[strategy][sell-market]")
{
    auto &book = core::OrderBook::getInstance();
    book.clear();

    SECTION("Sell market matches immediately at best bid")
    {
        addOrder("bid1", "buyer", 10, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto incoming = makeOrder("sell1", "seller", 5, 0.0, dto::Side::SELL, dto::OrderType::MARKET);

        core::SellMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].buyOrderId() == "bid1");
        REQUIRE(trades[0].sellOrderId() == "sell1");
        REQUIRE(trades[0].buyUserId() == "buyer");
        REQUIRE(trades[0].sellUserId() == "seller");
        REQUIRE(trades[0].price() == 100.0);
        REQUIRE(trades[0].quantity() == 5);
        // Bid partially filled (remaining 5)
        REQUIRE(book.getBestBid() != nullptr);
        REQUIRE(book.getBestBid()->quantity() == 5);
    }

    SECTION("Sell market consumes multiple bids if needed")
    {
        addOrder("bid_high", "buyer", 5, 105.0, dto::Side::BUY, dto::OrderType::LIMIT);
        addOrder("bid_low", "buyer", 5, 95.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto incoming = makeOrder("sell1", "seller", 8, 0.0, dto::Side::SELL, dto::OrderType::MARKET);

        core::SellMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 2);
        REQUIRE(trades[0].quantity() == 5);
        REQUIRE(trades[0].price() == 105.0);
        REQUIRE(trades[1].quantity() == 3);
        REQUIRE(trades[1].price() == 95.0);
        // bid_low partially filled (remaining 2)
        REQUIRE(book.getBestBid() != nullptr);
        REQUIRE(book.getBestBid()->quantity() == 2);
    }

    SECTION("Sell market partial fill if insufficient bid volume")
    {
        addOrder("bid1", "buyer", 3, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto incoming = makeOrder("sell1", "seller", 10, 0.0, dto::Side::SELL, dto::OrderType::MARKET);

        core::SellMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.size() == 1);
        REQUIRE(trades[0].quantity() == 3);
        REQUIRE(book.getBestBid() == nullptr);
    }

    SECTION("Sell market no bids available")
    {
        auto incoming = makeOrder("sell1", "seller", 5, 0.0, dto::Side::SELL, dto::OrderType::MARKET);

        core::SellMarketStrategy strategy;
        auto trades = strategy.match(incoming, book);

        REQUIRE(trades.empty());
    }

    SECTION("Sell market updates LTP")
    {
        addOrder("bid1", "buyer", 5, 100.0, dto::Side::BUY, dto::OrderType::LIMIT);
        auto incoming = makeOrder("sell1", "seller", 5, 0.0, dto::Side::SELL, dto::OrderType::MARKET);

        core::SellMarketStrategy strategy;
        strategy.match(incoming, book);

        REQUIRE(book.getLTP() == 100.0);
    }
}
