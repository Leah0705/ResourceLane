/**
 * @file Order.cpp
 * @brief Implementation of the Order class.
 */

#include "core/Order.hpp"
namespace core
{

  /**
   * @brief Constructs an Order object from a DTO.
   * 
   * @param dto The OrderData data transfer object containing all necessary parameters.
   */
  Order::Order(const dto::OrderData &dto)
      : order_id_(dto.order_id),
        user_id_(dto.user_id),
        quantity_(dto.quantity),
        price_(dto.price),
        side_(dto.side),
        type_(dto.type) {}

  /**
   * @brief Accessor for the order's unique identifier.
   * @return const std::string& The order ID.
   */
  const std::string &Order::order_id() const { return order_id_; }

  /**
   * @brief Accessor for the user's identifier who placed the order.
   * @return const std::string& The user ID.
   */
  const std::string &Order::user_id() const { return user_id_; }

  /**
   * @brief Accessor for the current remaining quantity of the order.
   * @return int The quantity left to match.
   */
  int Order::quantity() const { return quantity_; }

  /**
   * @brief Accessor for the limit price of the order.
   * @return double The order price.
   */
  double Order::price() const { return price_; }

  /**
   * @brief Accessor for the side of the order (BUY/SELL).
   * @return dto::Side The order side enum.
   */
  dto::Side Order::side() const { return side_; }

  /**
   * @brief Accessor for the type of the order (LIMIT/MARKET).
   * @return dto::OrderType The order type enum.
   */
  dto::OrderType Order::type() const { return type_; }

  /**
   * @brief Mutator to update the remaining quantity of the order after a partial or full match.
   * @param q The new quantity.
   */
  void Order::setQuantity(int q) { quantity_ = q; }
} // namespace core