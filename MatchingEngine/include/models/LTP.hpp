#pragma once
#include <chrono>

namespace models {

/**
 * @class LTP
 * @brief Represents the Last Traded Price (LTP) information.
 */
class LTP {
    double price_;
    std::chrono::system_clock::time_point ts_;

public:
    /**
     * @brief Constructs an LTP object.
     * @param price The price of the last trade.
     * @param ts The timestamp of the trade.
     */
    LTP(double price, std::chrono::system_clock::time_point ts);

    /**
     * @brief Gets the price.
     * @return The last traded price.
     */
    double price() const;

    /**
     * @brief Gets the timestamp.
     * @return The time of the trade.
     */
    std::chrono::system_clock::time_point timestamp() const;
};

} // namespace models
