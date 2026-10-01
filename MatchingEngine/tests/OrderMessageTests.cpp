#include <catch2/catch_test_macros.hpp>
#include "kafka/OrderMessageParser.hpp"
#include "service/OrderMatchingService.hpp"
#include "service/RecentIdWindow.hpp"
#include "core/Order.hpp"
#include <stdexcept>

TEST_CASE("Order messages are parsed and validated", "[parser]")
{
    SECTION("A message without an action is a new order")
    {
        auto message = kafka::parseOrderMessage(
            R"({"orderId":"7","userId":"3","quantity":5,"price":10.25,"side":"SELL","type":"LIMIT"})");
        REQUIRE(message.action == dto::OrderAction::NEW);
        REQUIRE(message.order.order_id == "7");
        REQUIRE(message.order.quantity == 5);
        REQUIRE(message.order.side == dto::Side::SELL);
        REQUIRE(message.order.type == dto::OrderType::LIMIT);
    }

    SECTION("A cancel needs only the order and user IDs")
    {
        auto message = kafka::parseOrderMessage(R"({"action":"CANCEL","orderId":"7","userId":"3"})");
        REQUIRE(message.action == dto::OrderAction::CANCEL);
        REQUIRE(message.order.order_id == "7");
        REQUIRE(message.order.user_id == "3");
    }

    SECTION("Bad input is reported as invalid_argument, never as a crash-worthy error")
    {
        for (const char *payload : {
                 "",
                 "{not json",
                 "[1,2]",
                 R"({"orderId":"7","userId":"3"})",
                 R"({"orderId":7,"userId":"3","quantity":5,"price":10,"side":"BUY","type":"LIMIT"})",
                 R"({"orderId":"7","userId":"3","quantity":0,"price":10,"side":"BUY","type":"LIMIT"})",
                 R"({"orderId":"7","userId":"3","quantity":5,"price":-1,"side":"BUY","type":"LIMIT"})",
                 R"({"orderId":"7","userId":"3","quantity":5,"price":10,"side":"HOLD","type":"LIMIT"})",
                 R"({"orderId":"7","userId":"3","quantity":5,"price":10,"side":"BUY","type":"STOP"})",
                 R"({"action":"AMEND","orderId":"7","userId":"3"})",
                 R"({"action":"CANCEL","orderId":"","userId":"3"})"})
        {
            CAPTURE(payload);
            REQUIRE_THROWS_AS(kafka::parseOrderMessage(payload), std::invalid_argument);
        }
    }
}

TEST_CASE("The recent ID window stays bounded", "[dedup]")
{
    core::RecentIdWindow window(2);
    REQUIRE(window.insert("a"));
    REQUIRE_FALSE(window.insert("a"));
    REQUIRE(window.insert("b"));
    REQUIRE(window.insert("c"));
    REQUIRE(window.size() == 2);
    REQUIRE_FALSE(window.contains("a"));
    REQUIRE(window.contains("b"));
    REQUIRE(window.contains("c"));
    REQUIRE_THROWS(core::RecentIdWindow(0));
}

TEST_CASE("Cancels and redelivered messages", "[service]")
{
    auto &book = core::OrderBook::getInstance();
    book.clear();
    core::OrderMatchingService service;

    SECTION("A cancel removes only the owner's resting order, once")
    {
        service.handleOrder({"bid-1", "buyer", 4, 10.0, dto::Side::BUY, dto::OrderType::LIMIT});
        service.handleOrder({"bid-2", "buyer", 2, 10.0, dto::Side::BUY, dto::OrderType::LIMIT});

        service.handleCancel("bid-1", "someone-else");
        REQUIRE(book.getBids().at(10.0).size() == 2);

        book.clear();
        service.handleOrder({"bid-3", "buyer", 4, 10.0, dto::Side::BUY, dto::OrderType::LIMIT});
        service.handleCancel("bid-3", "buyer");
        REQUIRE(book.getBids().empty());

        service.handleOrder({"bid-4", "buyer", 1, 10.0, dto::Side::BUY, dto::OrderType::LIMIT});
        service.handleCancel("bid-3", "buyer");
        REQUIRE(book.getBids().at(10.0).front().order_id() == "bid-4");
    }

    SECTION("A partly filled order cancels only what is left")
    {
        service.handleOrder({"ask-1", "seller", 5, 10.0, dto::Side::SELL, dto::OrderType::LIMIT});
        service.handleOrder({"bid-1", "buyer", 2, 10.0, dto::Side::BUY, dto::OrderType::LIMIT});
        REQUIRE(book.getBestAsk()->quantity() == 3);

        service.handle({dto::OrderAction::CANCEL, {"ask-1", "seller", 0, 0.0, dto::Side::BUY, dto::OrderType::LIMIT}});
        REQUIRE(book.getAsks().empty());
    }

    SECTION("A republished order is ignored while it still rests in the book")
    {
        service.handleOrder({"ask-1", "seller", 5, 10.0, dto::Side::SELL, dto::OrderType::LIMIT});
        service.handleOrder({"ask-1", "seller", 5, 10.0, dto::Side::SELL, dto::OrderType::LIMIT});
        REQUIRE(book.getAsks().at(10.0).size() == 1);
    }

    SECTION("Applied offsets are recognised")
    {
        REQUIRE_FALSE(service.alreadyApplied({0, 0}));
        service.handleOrder({"bid-9", "buyer", 1, 9.0, dto::Side::BUY, dto::OrderType::LIMIT},
                            core::MessagePosition{0, 12});
        REQUIRE(service.alreadyApplied({0, 12}));
        REQUIRE(service.alreadyApplied({0, 3}));
        REQUIRE_FALSE(service.alreadyApplied({0, 13}));
        REQUIRE_FALSE(service.alreadyApplied({1, 0}));
    }

    book.clear();
}
