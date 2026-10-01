#include "service/OrderBookStateStore.hpp"
#include "core/Order.hpp"
#include <nlohmann/json.hpp>
#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <filesystem>
#include <fstream>
#include <stdexcept>
#include <unistd.h>

namespace core {
namespace {
void writeOrder(nlohmann::json& result, const Order& order) {
    result.push_back({{"orderId", order.order_id()}, {"userId", order.user_id()},
                      {"quantity", order.quantity()}, {"price", order.price()},
                      {"side", order.side() == dto::Side::BUY ? "BUY" : "SELL"}});
}

void writeFully(int fd, const std::string& data) {
    size_t offset = 0;
    while (offset < data.size()) {
        ssize_t n = ::write(fd, data.data() + offset, data.size() - offset);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) throw std::runtime_error("Cannot write matcher state: " + std::string(std::strerror(errno)));
        offset += static_cast<size_t>(n);
    }
}
}

OrderBookStateStore::OrderBookStateStore(std::string path) : path_(std::move(path)) {}

void OrderBookStateStore::load(OrderBook& book, std::unordered_set<std::string>& processedIds) const {
    if (!std::filesystem::exists(path_)) return;
    std::ifstream input(path_);
    if (!input) throw std::runtime_error("Cannot open matcher state");
    auto state = nlohmann::json::parse(input);
    if (state.at("version") != 1) throw std::runtime_error("Unsupported matcher state version");
    book.clear();
    for (const auto& item : state.at("orders")) {
        const auto side = item.at("side").get<std::string>();
        if (side != "BUY" && side != "SELL") throw std::runtime_error("Invalid persisted side");
        dto::OrderData dto{item.at("orderId").get<std::string>(),
                           item.at("userId").get<std::string>(),
                           item.at("quantity").get<int>(),
                           item.at("price").get<double>(),
                           side == "BUY" ? dto::Side::BUY : dto::Side::SELL,
                           dto::OrderType::LIMIT};
        if (dto.quantity <= 0) throw std::runtime_error("Invalid persisted quantity");
        book.addOrder(Order(dto));
    }
    book.updateLTP(state.at("ltp").get<double>());
    processedIds = state.at("processedOrderIds").get<std::unordered_set<std::string>>();
}

void OrderBookStateStore::save(const OrderBook& book,
                               const std::unordered_set<std::string>& processedIds) const {
    nlohmann::json orders = nlohmann::json::array();
    for (const auto& [price, level] : book.getBids())
        for (const auto& order : level) writeOrder(orders, order);
    for (const auto& [price, level] : book.getAsks())
        for (const auto& order : level) writeOrder(orders, order);
    nlohmann::json state{{"version", 1}, {"ltp", book.getLTP()},
                         {"orders", orders}, {"processedOrderIds", processedIds}};
    const std::string data = state.dump();
    const std::string temporary = path_ + ".tmp";
    int fd = ::open(temporary.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) throw std::runtime_error("Cannot open matcher state temp file");
    try {
        writeFully(fd, data);
        if (::fsync(fd) != 0) throw std::runtime_error("Cannot sync matcher state");
    } catch (...) {
        ::close(fd);
        throw;
    }
    if (::close(fd) != 0) throw std::runtime_error("Cannot close matcher state");
    std::filesystem::rename(temporary, path_);
    auto directory = std::filesystem::path(path_).parent_path();
    if (directory.empty()) directory = ".";
    int dirfd = ::open(directory.c_str(), O_RDONLY);
    if (dirfd < 0 || ::fsync(dirfd) != 0) {
        if (dirfd >= 0) ::close(dirfd);
        throw std::runtime_error("Cannot sync matcher state directory");
    }
    ::close(dirfd);
}
}
