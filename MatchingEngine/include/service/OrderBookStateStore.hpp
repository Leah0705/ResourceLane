#pragma once

#include "core/OrderBook.hpp"
#include "service/RecentIdWindow.hpp"
#include <cstdint>
#include <map>
#include <string>

namespace core {
/** @brief Last applied Kafka offset per partition of the order topic. */
using PartitionOffsets = std::map<int, std::int64_t>;

class OrderBookStateStore {
public:
    explicit OrderBookStateStore(std::string path);
    /** @brief Restores the book, recent message IDs and applied offsets. Accepts version 1 and 2 files. */
    void load(OrderBook& book, RecentIdWindow& recentIds, PartitionOffsets& offsets) const;
    /** @brief Atomically writes a version 2 snapshot. */
    void save(const OrderBook& book, const RecentIdWindow& recentIds, const PartitionOffsets& offsets) const;

private:
    std::string path_;
};
}
