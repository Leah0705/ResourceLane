#pragma once

#include "core/OrderBook.hpp"
#include <string>
#include <unordered_set>

namespace core {
class OrderBookStateStore {
public:
    explicit OrderBookStateStore(std::string path);
    void load(OrderBook& book, std::unordered_set<std::string>& processedIds) const;
    void save(const OrderBook& book, const std::unordered_set<std::string>& processedIds) const;

private:
    std::string path_;
};
}
