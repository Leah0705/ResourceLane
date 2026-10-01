/**
 * @file Trade.cpp
 * @brief Implementation of the Trade class.
 */

#include "models/Trade.hpp"

namespace models
{

  /**
   * @brief Constructs a Trade representing a successful order match.
   * 
   * @param buyOrderId The ID of the buy order involved in the trade.
   * @param sellOrderId The ID of the sell order involved in the trade.
   * @param buyUserId The user ID of the buyer.
   * @param sellUserId The user ID of the seller.
   * @param price The matched price.
   * @param qty The matched quantity.
   * @param ts The timestamp when the match occurred.
   */
  Trade::Trade(std::string buyOrderId, std::string sellOrderId,
               std::string buyUserId, std::string sellUserId,
               double price, int qty,
               std::chrono::system_clock::time_point ts)
      : executionId_(buyOrderId + ":" + sellOrderId),
        buyOrderId_(std::move(buyOrderId)),
        sellOrderId_(std::move(sellOrderId)),
        buyUserId_(std::move(buyUserId)),
        sellUserId_(std::move(sellUserId)),

        price_(price),
        quantity_(qty),
        ts_(ts)
  {
  }

  const std::string &Trade::executionId() const { return executionId_; }

  /**
   * @brief Gets the buy order ID.
   * @return The buy order ID string.
   */
  const std::string &Trade::buyOrderId() const { return buyOrderId_; }

  /**
   * @brief Gets the sell order ID.
   * @return The sell order ID string.
   */
  const std::string &Trade::sellOrderId() const { return sellOrderId_; }

  /**
   * @brief Gets the buyer's user ID.
   * @return The buyer's user ID string.
   */
  const std::string &Trade::buyUserId() const { return buyUserId_; }

  /**
   * @brief Gets the seller's user ID.
   * @return The seller's user ID string.
   */
  const std::string &Trade::sellUserId() const { return sellUserId_; }

  /**
   * @brief Gets the price at which the trade occurred.
   * @return The executed price.
   */
  double Trade::price() const { return price_; }

  /**
   * @brief Gets the quantity executed in this trade.
   * @return The traded quantity.
   */
  int Trade::quantity() const { return quantity_; }

  /**
   * @brief Gets the time at which the match was made.
   * @return The timestamp.
   */
  std::chrono::system_clock::time_point Trade::timestamp() const
  {
    return ts_;
  }

} // namespace models
