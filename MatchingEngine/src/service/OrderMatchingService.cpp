/**
 * @file OrderMatchingService.cpp
 * @brief Implementation of the OrderMatchingService class.
 */

#include "service/OrderMatchingService.hpp"
#include "models/LTP.hpp"
#include "models/OrderBookSnapshot.hpp"
#include "core/StrategyFactory.hpp"
#include <chrono>
#include <iostream>
#include <cstdlib>
#include <stdexcept>
#include <unordered_map>

namespace core
{
    namespace
    {
        constexpr std::size_t kDefaultDedupWindow = 10000;

        std::size_t dedupWindowFromEnv()
        {
            const char *value = std::getenv("MATCHER_DEDUP_WINDOW");
            if (!value || !*value)
                return kDefaultDedupWindow;
            std::size_t parsed = 0;
            const long long window = std::stoll(value, &parsed);
            if (parsed != std::string(value).size() || window <= 0)
                throw std::invalid_argument("MATCHER_DEDUP_WINDOW must be a positive integer");
            return static_cast<std::size_t>(window);
        }
    } // namespace

    /**
     * @brief Constructor for OrderMatchingService.
     * 
     * Initializes the service by acquiring the singleton instance of the OrderBook.
     * It also implicitly initializes the Kafka producers for trades, LTP, and orderbook snapshots.
     */
    OrderMatchingService::OrderMatchingService()
        : book_(OrderBook::getInstance()), recentIds_(dedupWindowFromEnv())
    {
        const char* statePath = std::getenv("MATCHER_STATE_FILE");
        if (statePath && *statePath) {
            stateStore_ = std::make_unique<OrderBookStateStore>(statePath);
            stateStore_->load(book_, recentIds_, offsets_);
        }
    }

    bool OrderMatchingService::alreadyApplied(const MessagePosition &position) const
    {
        auto it = offsets_.find(position.partition);
        return it != offsets_.end() && position.offset <= it->second;
    }

    void OrderMatchingService::handle(const dto::OrderMessage &message, std::optional<MessagePosition> position)
    {
        if (message.action == dto::OrderAction::CANCEL)
            handleCancel(message.order.order_id, message.order.user_id, position);
        else
            handleOrder(message.order, position);
    }

    /**
     * @brief Core handler for processing an incoming order.
     * 
     * Takes an OrderData DTO, creates an Order object, and determines the appropriate 
     * matching strategy via the StrategyFactory. It then executes the strategy against 
     * the order book.
     * Finally, it publishes any resulting trades, the new Last Traded Price (LTP),
     * and the updated OrderBook snapshot to Kafka.
     * 
     * @param dto The data transfer object containing the incoming order's parameters.
     */
    void OrderMatchingService::handleOrder(const dto::OrderData &dto, std::optional<MessagePosition> position)
    {
        // The trade-service outbox can republish after an uncertain Kafka ack, at a new offset.
        // Recent order IDs catch those copies; an order still resting in the book is caught
        // even after its ID has left the window.
        if (findResting(dto.order_id) || !recentIds_.insert(dto.order_id))
            return;

        Order order = OrderFactory::create(dto);
        std::shared_ptr<IMatchingStrategy> strategy = StrategyFactory::create(dto);
        auto trades = strategy->match(order, book_);
        for (auto &t : trades)
        {
            tradeProd_.publish(t);
        }

        struct AffectedOrder { std::string userId; std::string side; int filled{0}; };
        std::unordered_map<std::string, AffectedOrder> affected;
        for (const auto& trade : trades) {
            auto& buy = affected[trade.buyOrderId()];
            buy.userId = trade.buyUserId();
            buy.side = "BUY";
            buy.filled += trade.quantity();
            auto& sell = affected[trade.sellOrderId()];
            sell.userId = trade.sellUserId();
            sell.side = "SELL";
            sell.filled += trade.quantity();
        }
        if (dto.type == dto::OrderType::MARKET) {
            auto& market = affected[dto.order_id];
            market.userId = dto.user_id;
            market.side = dto.side == dto::Side::BUY ? "BUY" : "SELL";
        }

        auto remainingInBook = [this](const std::string& id) {
            for (const auto& [price, level] : book_.getBids())
                for (const auto& order : level)
                    if (order.order_id() == id) return order.quantity();
            for (const auto& [price, level] : book_.getAsks())
                for (const auto& order : level)
                    if (order.order_id() == id) return order.quantity();
            return 0;
        };
        for (const auto& [id, state] : affected) {
            bool completed = id == dto.order_id && dto.type == dto::OrderType::MARKET;
            statusProd_.publish(models::Status(id, state.userId, state.side,
                                completed ? 0 : remainingInBook(id), state.filled,
                                completed, std::chrono::system_clock::now()));
        }
        if (!trades.empty())
        {
            models::LTP ltp(book_.getLTP(),
                            std::chrono::system_clock::now());
            ltpProd_.publish(ltp);
        }

        models::OrderBookSnapshot snapshot(book_.getBids(), book_.getAsks(), std::chrono::system_clock::now()); // Pass the order book to the constructor
        obProd_.publish(snapshot);
        recordAndSave(position);
    }

    void OrderMatchingService::handleCancel(const std::string &orderId, const std::string &userId,
                                            std::optional<MessagePosition> position)
    {
        // A cancel has its own ID so it is not mistaken for a copy of the original order.
        if (!recentIds_.insert("cancel:" + orderId))
            return;

        int cancelled = 0;
        std::string side;
        const Order *resting = findResting(orderId);
        if (resting && resting->user_id() == userId)
        {
            const Order removed = *resting;
            cancelled = removed.quantity();
            side = removed.side() == dto::Side::BUY ? "BUY" : "SELL";
            book_.removeOrder(removed);
        }
        statusProd_.publish(models::Status::cancellation(orderId, userId, side, cancelled,
                                                         std::chrono::system_clock::now()));
        if (cancelled > 0)
        {
            models::OrderBookSnapshot snapshot(book_.getBids(), book_.getAsks(), std::chrono::system_clock::now());
            obProd_.publish(snapshot);
        }
        recordAndSave(position);
    }

    const Order *OrderMatchingService::findResting(const std::string &orderId) const
    {
        for (const auto &[price, level] : book_.getBids())
            for (const auto &order : level)
                if (order.order_id() == orderId)
                    return &order;
        for (const auto &[price, level] : book_.getAsks())
            for (const auto &order : level)
                if (order.order_id() == orderId)
                    return &order;
        return nullptr;
    }

    void OrderMatchingService::recordAndSave(std::optional<MessagePosition> position)
    {
        if (position)
            offsets_[position->partition] = position->offset;
        if (stateStore_)
            stateStore_->save(book_, recentIds_, offsets_);
    }

} // namespace core
