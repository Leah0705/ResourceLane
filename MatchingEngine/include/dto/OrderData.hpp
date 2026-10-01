#pragma once
#include <string>

namespace dto
{

    /**
     * @enum Side
     * @brief Represents the side of an order.
     */
    enum class Side
    {
        BUY,
        SELL
    };

    /**
     * @enum OrderType
     * @brief Represents the type of an order.
     */
    enum class OrderType
    {
        MARKET,
        LIMIT
    };

    /**
     * @struct OrderData
     * @brief Data Transfer Object for carrying order information.
     */
    struct OrderData
    {
        std::string order_id; /**< Unique identifier for the order */
        std::string user_id;  /**< Identifier for the user who placed the order */
        int quantity;         /**< Quantity of the asset */
        double price;         /**< Price per unit (ignored for Market orders) */
        dto::Side side;       /**< Side of the order (Buy/Sell) */
        dto::OrderType type;  /**< Type of the order (Market/Limit) */
    };

} // namespace dto
