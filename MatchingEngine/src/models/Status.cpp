/**
 * @file Status.cpp
 * @brief Implementation of the Status class.
 */

#include "models/Status.hpp"

namespace models
{

    /**
     * @brief Constructs a Status object representing an order update.
     * 
     * @param orderId The ID of the order being updated.
     * @param userId The ID of the user who owns the order.
     * @param side "BUY" or "SELL".
     * @param quantity The quantity filled or status update amount.
     * @param ts The timestamp of the update.
     */
    Status::Status(std::string orderId, std::string userId, std::string side, int quantity, std::chrono::system_clock::time_point ts)
        : orderId_(std::move(orderId)),
          userId_(std::move(userId)),
          side_(std::move(side)),
          quantity_(quantity),
          ts_(ts)
    {
    }

    Status::Status(std::string orderId, std::string userId, std::string side,
                   int remainingQuantity, int matchedQuantity, bool completed,
                   std::chrono::system_clock::time_point ts)
        : orderId_(std::move(orderId)), userId_(std::move(userId)), side_(std::move(side)),
          quantity_(matchedQuantity), remainingQuantity_(remainingQuantity),
          matchedQuantity_(matchedQuantity), completed_(completed), ts_(ts)
    {
    }

    /**
     * @brief Gets the order ID associated with this status.
     * @return The order ID string.
     */
    const std::string &Status::orderId() const { return orderId_; }

    /**
     * @brief Gets the user ID associated with this status.
     * @return The user ID string.
     */
    const std::string &Status::userId() const { return userId_; }

    /**
     * @brief Gets the side of the order (BUY/SELL).
     * @return The side string.
     */
    const std::string &Status::side() const { return side_; }

    /**
     * @brief Gets the updated quantity of the order.
     * @return The updated quantity.
     */
    int Status::quantity() const { return quantity_; }
    int Status::remainingQuantity() const { return remainingQuantity_; }
    int Status::matchedQuantity() const { return matchedQuantity_; }
    bool Status::completed() const { return completed_; }

    Status Status::cancellation(std::string orderId, std::string userId, std::string side,
                                int cancelledQuantity, std::chrono::system_clock::time_point ts)
    {
        Status status(std::move(orderId), std::move(userId), std::move(side), 0, 0, true, ts);
        status.cancelled_ = true;
        status.cancelledQuantity_ = cancelledQuantity;
        return status;
    }

    bool Status::cancelled() const { return cancelled_; }

    int Status::cancelledQuantity() const { return cancelledQuantity_; }

    /**
     * @brief Gets the timestamp when this status was generated.
     * @return The timestamp.
     */
    std::chrono::system_clock::time_point Status::timestamp() const { return ts_; }

} // namespace models
