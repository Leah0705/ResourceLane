#pragma once
#include "dto/OrderData.hpp"
#include <string>

namespace kafka
{
    /**
     * @brief Parses and validates one message from the order topic.
     *
     * A message without an "action" field is a new order. {"action":"CANCEL"} needs only
     * orderId and userId.
     *
     * @throws std::invalid_argument when the payload is malformed or fails validation.
     */
    dto::OrderMessage parseOrderMessage(const std::string &payload);

} // namespace kafka
