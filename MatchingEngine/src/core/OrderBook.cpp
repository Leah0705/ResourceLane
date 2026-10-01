/**
 * @file OrderBook.cpp
 * @brief Implementation of the OrderBook class.
 */

#include "core/Order.hpp"
#include "core/OrderBook.hpp"
#include <algorithm>

namespace core
{

    /**
     * @brief Adds an order to the order book.
     * 
     * Determines whether the order is a BUY or SELL, and adds it to the appropriate
     * internal map (bids_ or asks_) organized by price and insertion time (as a list).
     * 
     * @param o The order object containing side, price, and other details.
     */
    void OrderBook::addOrder(const Order &o)
    {
        if (o.side() == dto::Side::BUY)
            bids_[o.price()].push_back(o);
        else
            asks_[o.price()].push_back(o);
    }

    /**
     * @brief Removes a specific order from the order book.
     * 
     * Searches for the given order by ID within its corresponding price level
     * in the bids or asks map. If found, it removes the order. Additionally,
     * if the price level becomes empty after removal, it erases the price level.
     * 
     * @param o The order to be removed.
     */
    void OrderBook::removeOrder(const Order &o)
    {
        if (o.side() == dto::Side::BUY)
        {
            auto it = bids_.find(o.price());
            if (it == bids_.end())
                return;
            it->second.remove_if([&](const Order &ord)
                                 { return ord.order_id() == o.order_id(); });
            if (it->second.empty())
                bids_.erase(it);
        }
        else
        {
            auto it = asks_.find(o.price());
            if (it == asks_.end())
                return;
            it->second.remove_if([&](const Order &ord)
                                 { return ord.order_id() == o.order_id(); });
            if (it->second.empty())
                asks_.erase(it);
        }
    }

    /**
     * @brief Updates the Last Traded Price (LTP).
     * 
     * @param price The new price at which the last trade was executed.
     */
    void OrderBook::updateLTP(double price)
    {
        lastTradedPrice_ = price;
    }

    /**
     * @brief Retrieves the Last Traded Price.
     * 
     * @return The most recent transaction price.
     */
    double OrderBook::getLTP() const
    {
        return lastTradedPrice_;
    }

    /**
     * @brief Retrieves the singleton instance of the OrderBook.
     * 
     * Uses a statically allocated local variable to ensure thread-safe initialization
     * in C++11 and later.
     * 
     * @return A reference to the single OrderBook instance.
     */
    OrderBook &OrderBook::getInstance()
    {
        static OrderBook instance;
        return instance;
    }

    /**
     * @brief Retrieves the highest priced buy order (Best Bid).
     * 
     * Since bids_ is sorted in descending order by price, the first element 
     * represents the highest price. Returns the first order from its list (time priority).
     * 
     * @return Pointer to the best bid order, or nullptr if there are no bids.
     */
    Order *OrderBook::getBestBid()
    {
        if (bids_.empty())
            return nullptr;

        auto &lst = bids_.begin()->second;

        return lst.empty() ? nullptr : &lst.front();
    }

    /**
     * @brief Retrieves the lowest priced sell order (Best Ask).
     * 
     * Since asks_ is sorted in ascending order by price, the first element 
     * represents the lowest price. Returns the first order from its list (time priority).
     * 
     * @return Pointer to the best ask order, or nullptr if there are no asks.
     */
    Order *OrderBook::getBestAsk()
    {
        if (asks_.empty())
            return nullptr;

        auto &lst = asks_.begin()->second;

        return lst.empty() ? nullptr : &lst.front();
    }
    
    /**
     * @brief Returns a constant reference to the bids map.
     * 
     * @return A map of buy orders sorted by price (descending).
     */
    const std::map<double, std::list<Order>, std::greater<>>& OrderBook::getBids() const
    {
        return bids_;
    }

    /**
     * @brief Returns a constant reference to the asks map.
     * 
     * @return A map of sell orders sorted by price (ascending).
     */
    const std::map<double, std::list<Order>>& OrderBook::getAsks() const
    {
        return asks_;
    }

    /**
     * @brief Clears all orders from the book and resets the LTP to 0.0.
     * 
     * Typically used for resetting state during testing or market resets.
     */
    void OrderBook::clear()
    {
        bids_.clear();
        asks_.clear();
        lastTradedPrice_ = 0.0;
    }
} // namespace core
