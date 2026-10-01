#pragma once
#include <map>
#include <list>
#include <functional>

namespace core
{

    class Order; // Forward declaration

    /**
     * @class OrderBook
     * @brief Singleton class that manages the order book for the matching engine.
     *
     * The OrderBook maintains the lists of buy (bids) and sell (asks) orders,
     * sorted by price and time. It provides methods to add, remove, and retrieve
     * orders, as well as track the Last Traded Price (LTP).
     */
    class OrderBook
    {
    private:
        OrderBook() = default;

        OrderBook(const OrderBook &) = delete;
        OrderBook &operator=(const OrderBook &) = delete;
        OrderBook(OrderBook &&) = delete;
        OrderBook &operator=(OrderBook &&) = delete;

        std::map<double, std::list<Order>, std::greater<>> bids_;
        std::map<double, std::list<Order>> asks_;
        double lastTradedPrice_{0.0};

    public:
        static OrderBook &getInstance();

        /**
         * @brief Adds an order to the order book.
         * @param o The order to add.
         */
        void addOrder(const Order &o);

        /**
         * @brief Removes an order from the order book.
         * @param o The order to remove.
         */
        void removeOrder(const Order &o);

        /**
         * @brief Updates the Last Traded Price (LTP).
         * @param price The new price.
         */
        void updateLTP(double price);

        /**
         * @brief Gets the Last Traded Price (LTP).
         * @return The current LTP.
         */
        double getLTP() const;

        /**
         * @brief Gets the best bid order (highest price).
         * @return Pointer to the best bid order, or nullptr if empty.
         */
        Order *getBestBid();

        /**
         * @brief Gets the best ask order (lowest price).
         * @return Pointer to the best ask order, or nullptr if empty.
         */
        Order *getBestAsk();

        /**
         * @brief Retrieves the map of bids.
         * @return A map of bids sorted by price in descending order.
         */
        const std::map<double, std::list<Order>, std::greater<>>& getBids() const;

        /**
         * @brief Retrieves the map of asks.
         * @return A map of asks sorted by price in ascending order.
         */
        const std::map<double, std::list<Order>>& getAsks() const;

        /**
         * @brief Clears all orders and resets LTP.
         */
        void clear();
    };

} // namespace core
