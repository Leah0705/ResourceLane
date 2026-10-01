#include <catch2/catch_test_macros.hpp>
#include "service/OrderBookStateStore.hpp"
#include "core/Order.hpp"
#include <filesystem>
#include <fstream>
#include <unordered_set>
#include <unistd.h>

TEST_CASE("Resting orders and processed IDs survive a state reload", "[recovery]") {
    auto& book = core::OrderBook::getInstance();
    book.clear();
    const auto path = (std::filesystem::temp_directory_path() /
                       ("matcher-state-" + std::to_string(::getpid()) + ".json")).string();
    core::OrderBookStateStore store(path);
    book.addOrder(core::Order(dto::OrderData{"bid-1", "buyer", 3, 10.0,
                                             dto::Side::BUY, dto::OrderType::LIMIT}));
    book.addOrder(core::Order(dto::OrderData{"bid-2", "buyer", 2, 10.0,
                                             dto::Side::BUY, dto::OrderType::LIMIT}));
    book.addOrder(core::Order(dto::OrderData{"ask-1", "seller", 4, 12.0,
                                             dto::Side::SELL, dto::OrderType::LIMIT}));
    book.updateLTP(11.0);
    std::unordered_set<std::string> processed{"bid-1", "bid-2", "ask-1"};
    store.save(book, processed);

    book.clear();
    processed.clear();
    store.load(book, processed);

    REQUIRE(book.getBids().at(10.0).front().order_id() == "bid-1");
    REQUIRE(book.getBids().at(10.0).back().order_id() == "bid-2");
    REQUIRE(book.getAsks().at(12.0).front().quantity() == 4);
    REQUIRE(book.getLTP() == 11.0);
    REQUIRE(processed.size() == 3);
    REQUIRE(processed.count("bid-2") == 1);
    std::filesystem::remove(path);
    book.clear();
}

TEST_CASE("Corrupt matcher state fails instead of silently resetting the book", "[recovery]") {
    const auto path = (std::filesystem::temp_directory_path() /
                       ("matcher-corrupt-" + std::to_string(::getpid()) + ".json")).string();
    std::ofstream(path) << "{invalid";
    core::OrderBookStateStore store(path);
    std::unordered_set<std::string> processed;
    REQUIRE_THROWS(store.load(core::OrderBook::getInstance(), processed));
    std::filesystem::remove(path);
}
