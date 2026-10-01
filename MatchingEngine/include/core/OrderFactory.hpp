#pragma once
#include <memory>
#include "dto/OrderData.hpp"
#include "core/Order.hpp"

namespace core {

/**
 * @class OrderFactory
 * @brief Factory class for creating Order objects.
 */
class OrderFactory {
public:
    /**
     * @brief Creates an Order instance from OrderData DTO.
     * @param dto The data transfer object containing order details.
     * @return A new Order object.
     */
    static Order create(const dto::OrderData& dto);
};

} // namespace core
