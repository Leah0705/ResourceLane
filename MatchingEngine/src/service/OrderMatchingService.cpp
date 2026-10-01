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
#include <unordered_map>

namespace core
{
    /**
     * @brief Constructor for OrderMatchingService.
     * 
     * Initializes the service by acquiring the singleton instance of the OrderBook.
     * It also implicitly initializes the Kafka producers for trades, LTP, and orderbook snapshots.
     */
    OrderMatchingService::OrderMatchingService()
        : book_(OrderBook::getInstance())
    {
        const char* statePath = std::getenv("MATCHER_STATE_FILE");
        if (statePath && *statePath) {
            stateStore_ = std::make_unique<OrderBookStateStore>(statePath);
            stateStore_->load(book_, processedOrderIds_);
        }
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
    void OrderMatchingService::handleOrder(const dto::OrderData &dto)
    {
        // The trade-service outbox can republish after an uncertain Kafka ack.
        // Persisted order IDs suppress those duplicates across matcher restarts.
        if (!processedOrderIds_.insert(dto.order_id).second)
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
        if (stateStore_) stateStore_->save(book_, processedOrderIds_);
    }

} // namespace core
