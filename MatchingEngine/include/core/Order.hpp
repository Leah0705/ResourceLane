#pragma once
#include <memory>
#include <vector>
#include "dto/OrderData.hpp"
#include "models/Trade.hpp"

namespace core
{

    class OrderBook;

    /**
     * @class Order
     * @brief Represents an order in the trading system.
     *
     * Holds details about an order such as ID, user ID, quantity, price, side (Buy/Sell),
     * and type (Market/Limit).
     */
    class Order
    {
    private:
        std::string order_id_;
        std::string user_id_;
        int quantity_;
        double price_;
        dto::Side side_;
        dto::OrderType type_;

    public:
        /**
         * @brief Constructs an Order from OrderData DTO.
         * @param dto The data transfer object containing order details.
         */
        Order(const dto::OrderData &dto);

        /**
         * @brief Gets the order ID.
         * @return The unique identifier of the order.
         */
        const std::string &order_id() const;

        /**
         * @brief Gets the user ID.
         * @return The identifier of the user who placed the order.
         */
        const std::string &user_id() const;

        /**
         * @brief Gets the current quantity of the order.
         * @return The remaining quantity to be executed.
         */
        int quantity() const;

        /**
         * @brief Gets the price of the order.
         * @return The price per unit.
         */
        double price() const;

        /**
         * @brief Gets the side of the order (Buy/Sell).
         * @return The side of the order.
         */
        dto::Side side() const;

        /**
         * @brief Gets the type of the order (Market/Limit).
         * @return The type of the order.
         */
        dto::OrderType type() const;

        /**
         * @brief Sets the quantity of the order.
         * @param q The new quantity.
         */
        void setQuantity(int q);
    };

} // namespace core
