#include <catch2/catch_test_macros.hpp>
#include "service/OrderBookStateStore.hpp"
#include "core/Order.hpp"
#include <filesystem>
#include <fstream>
#include <unistd.h>

TEST_CASE("Resting orders, recent IDs and offsets survive a state reload", "[recovery]") {
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
    core::RecentIdWindow recent(10);
    for (const auto* id : {"bid-1", "bid-2", "ask-1", "cancel:bid-0"}) recent.insert(id);
    core::PartitionOffsets offsets{{0, 41}};
    store.save(book, recent, offsets);

    book.clear();
    core::RecentIdWindow loaded(10);
    core::PartitionOffsets loadedOffsets;
    store.load(book, loaded, loadedOffsets);

    REQUIRE(book.getBids().at(10.0).front().order_id() == "bid-1");
    REQUIRE(book.getBids().at(10.0).back().order_id() == "bid-2");
    REQUIRE(book.getAsks().at(12.0).front().quantity() == 4);
    REQUIRE(book.getLTP() == 11.0);
    REQUIRE(loaded.ordered() == recent.ordered());
    REQUIRE(loadedOffsets == offsets);
    std::filesystem::remove(path);
    book.clear();
}

TEST_CASE("A version 1 state file still loads", "[recovery]") {
    auto& book = core::OrderBook::getInstance();
    book.clear();
    const auto path = (std::filesystem::temp_directory_path() /
                       ("matcher-v1-" + std::to_string(::getpid()) + ".json")).string();
    std::ofstream(path) << R"({"version":1,"ltp":9.5,"orders":[{"orderId":"bid-1","userId":"u1",)"
                           R"("quantity":2,"price":9.0,"side":"BUY"}],"processedOrderIds":["bid-1","old-1","old-2"]})";
    core::OrderBookStateStore store(path);
    core::RecentIdWindow recent(2);
    core::PartitionOffsets offsets{{0, 7}};
    store.load(book, recent, offsets);

    REQUIRE(book.getBids().at(9.0).front().order_id() == "bid-1");
    REQUIRE(book.getLTP() == 9.5);
    REQUIRE(recent.size() == 2);
    REQUIRE(offsets.empty());
    std::filesystem::remove(path);
    book.clear();
}

TEST_CASE("Corrupt matcher state fails instead of silently resetting the book", "[recovery]") {
    const auto path = (std::filesystem::temp_directory_path() /
                       ("matcher-corrupt-" + std::to_string(::getpid()) + ".json")).string();
    std::ofstream(path) << "{invalid";
    core::OrderBookStateStore store(path);
    core::RecentIdWindow recent(10);
    core::PartitionOffsets offsets;
    REQUIRE_THROWS(store.load(core::OrderBook::getInstance(), recent, offsets));
    std::filesystem::remove(path);
}
