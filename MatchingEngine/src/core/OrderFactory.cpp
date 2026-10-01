/**
 * @file OrderFactory.cpp
 * @brief Implementation of the OrderFactory class.
 */

#include "core/OrderFactory.hpp"

namespace core
{

    /**
     * @brief Creates a new Order instance from a given OrderData DTO.
     * 
     * Provides a central point for order creation, abstracting away the instantiation
     * logic. This enables easier future enhancements, such as order validation or
     * dependency injection, before the object is fully formed.
     * 
     * @param dto The data transfer object containing order attributes.
     * @return Order The fully constructed Order object.
     */
    Order OrderFactory::create(const dto::OrderData &dto)
    {
        return Order(dto);
    }

} // namespace core
