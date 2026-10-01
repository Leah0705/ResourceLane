#pragma once
#include <string>
#include <chrono>

namespace models
{

    /**
     * @class Trade
     * @brief Represents an executed trade between a buyer and a seller.
     */
    class Trade
    {
        std::string executionId_;
        std::string buyOrderId_;
        std::string sellOrderId_;
        std::string buyUserId_;
        std::string sellUserId_;
        double price_;
        int quantity_;
        std::chrono::system_clock::time_point ts_;

    public:
        /** Stable identifier carried by every publication of this execution. */
        const std::string &executionId() const;

        /**
         * @brief Constructs a Trade object.
         * @param buyOrderId The ID of the buy order.
         * @param sellOrderId The ID of the sell order.
         * @param buyUserId The ID of the buyer.
         * @param sellUserId The ID of the seller.
         * @param price The execution price.
         * @param qty The quantity traded.
         * @param ts The timestamp of the trade.
         */
        Trade(std::string buyOrderId, std::string sellOrderId, std::string buyUserId, std::string sellUserId, double price, int qty, std::chrono::system_clock::time_point ts);

        /**
         * @brief Gets the buy order ID.
         * @return The buy order ID.
         */
        const std::string &buyOrderId() const;

        /**
         * @brief Gets the sell order ID.
         * @return The sell order ID.
         */
        const std::string &sellOrderId() const;

        /**
         * @brief Gets the buyer's user ID.
         * @return The buyer ID.
         */
        const std::string &buyUserId() const;

        /**
         * @brief Gets the seller's user ID.
         * @return The seller ID.
         */
        const std::string &sellUserId() const;

        /**
         * @brief Gets the execution price.
         * @return The price.
         */
        double price() const;

        /**
         * @brief Gets the traded quantity.
         * @return The quantity.
         */
        int quantity() const;

        /**
         * @brief Gets the timestamp of the trade.
         * @return The timestamp.
         */
        std::chrono::system_clock::time_point timestamp() const;
    };

} // namespace models
