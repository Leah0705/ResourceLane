#pragma once
#include <string>
#include <chrono>

namespace models
{

    /**
     * @class Status
     * @brief Represents the status of an order after execution or cancellation.
     */
    class Status
    {
        std::string orderId_;
        std::string userId_;
        std::string side_;
        int quantity_;
        int remainingQuantity_{-1};
        int matchedQuantity_{0};
        bool completed_{false};
        std::chrono::system_clock::time_point ts_;

    public:
        /**
         * @brief Constructs a Status object.
         * @param orderId The unique identifier of the order.
         * @param userId The identifier of the user who placed the order.
         * @param side The side of the order (Buy/Sell).
         * @param quantity The remaining or filled quantity.
         * @param ts The timestamp of the status update.
         */
        Status(std::string orderId, std::string userId,
               std::string side, int quantity, std::chrono::system_clock::time_point ts);
        Status(std::string orderId, std::string userId, std::string side,
               int remainingQuantity, int matchedQuantity, bool completed,
               std::chrono::system_clock::time_point ts);

        /**
         * @brief Gets the order ID.
         * @return The order ID.
         */
        const std::string &orderId() const;

        /**
         * @brief Gets the user ID.
         * @return The user ID.
         */
        const std::string &userId() const;

        /**
         * @brief Gets the side of the order.
         * @return The side string.
         */
        const std::string &side() const;

        /**
         * @brief Gets the quantity associated with the status.
         * @return The quantity.
         */
        int quantity() const;
        int remainingQuantity() const;
        int matchedQuantity() const;
        bool completed() const;

        /**
         * @brief Gets the timestamp of the status update.
         * @return The timestamp.
         */
        std::chrono::system_clock::time_point timestamp() const;
    };

} // namespace models
