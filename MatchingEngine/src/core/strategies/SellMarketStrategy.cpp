#include "core/strategies/SellMarketStrategy.hpp"
#include "core/OrderBook.hpp"
#include "core/Order.hpp"
#include <chrono>
#include <algorithm>

namespace core
{
    /**
     * @brief Constructor for SellMarketStrategy.
     */
    SellMarketStrategy::SellMarketStrategy() {}

    /**
     * @brief Matches an incoming Sell Market order against existing Buy orders in the OrderBook.
     * 
     * Iterates while the incoming order has remaining quantity and there are any
     * buy orders (bids) in the book. A match occurs at the best available bid price
     * regardless of the price. 
     * Generates Trade objects for executed matches. Any remainder is cancelled.
     * 
     * @param incoming The new Sell Market order to be matched.
     * @param book Reference to the OrderBook containing resting orders.
     * @return std::vector<models::Trade> List of trades resulting from the matches.
     */
    std::vector<models::Trade> SellMarketStrategy::match(Order &incoming, OrderBook &book)
    {
        int qty = incoming.quantity();
        std::vector<models::Trade> trades;
        while (qty > 0)
        {
            Order *bestBidPtr = book.getBestBid();
            if (!bestBidPtr)
            {
                break;
            }
            Order &bestBid = *bestBidPtr;
            int tradeQty = std::min(qty, bestBid.quantity());
            double price = bestBid.price();
            trades.emplace_back(models::Trade(bestBid.order_id(), incoming.order_id(), bestBid.user_id(), incoming.user_id(),
                                              price, tradeQty,
                                              std::chrono::system_clock::now()));
            qty -= tradeQty;
            bestBid.setQuantity(bestBid.quantity() - tradeQty);
            if (bestBid.quantity() == 0)
                book.removeOrder(bestBid);
        }
        incoming.setQuantity(qty);

        // Update the Last Traded Price (LTP) in the OrderBook if any trades occurred.
        if (!trades.empty())
            book.updateLTP(trades.back().price());
            
        return trades;
    }

    /**
     * @brief Destructor for SellMarketStrategy.
     */
    SellMarketStrategy::~SellMarketStrategy() {}
} // namespace core
