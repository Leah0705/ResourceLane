/**
 * @file LTP.cpp
 * @brief Implementation of the LTP class.
 */

#include "models/LTP.hpp"

namespace models {

    /**
     * @brief Constructs a Last Traded Price (LTP) event.
     * 
     * @param price The latest execution price.
     * @param ts The timestamp when this price was updated.
     */
    LTP::LTP(double price, std::chrono::system_clock::time_point ts)
        : price_(price), ts_(ts) {}

    /**
     * @brief Gets the most recent traded price.
     * @return The execution price.
     */
    double LTP::price() const { return price_; }

    /**
     * @brief Gets the time at which this LTP was recorded.
     * @return The timestamp.
     */
    std::chrono::system_clock::time_point LTP::timestamp() const {
        return ts_;
    }

} // namespace models
