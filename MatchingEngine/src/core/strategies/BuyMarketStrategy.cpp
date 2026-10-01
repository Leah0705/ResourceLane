#include "core/strategies/BuyMarketStrategy.hpp"
#include "core/OrderBook.hpp"
#include "core/Order.hpp" // Include for full definition of Order
#include <chrono>
#include <algorithm>

namespace core
{
    /**
     * @brief Constructor for BuyMarketStrategy.
     */
    BuyMarketStrategy::BuyMarketStrategy() {}

    /**
     * @brief Matches an incoming Buy Market order against existing Sell orders in the OrderBook.
     * 
     * Iterates while the incoming order has remaining quantity and there are any
     * sell orders (asks) in the book up to the submitted maximum price.
     * Generates Trade objects for executed matches. Any remainder is cancelled.
     * 
     * @param incoming The new Buy Market order to be matched.
     * @param book Reference to the OrderBook containing resting orders.
     * @return std::vector<models::Trade> List of trades resulting from the matches.
     */
    std::vector<models::Trade> BuyMarketStrategy::match(Order &incoming, OrderBook &book)
    {
        int qty = incoming.quantity();
        std::vector<models::Trade> trades;
        while (qty > 0)
        {
            Order *bestAskPtr = book.getBestAsk();
            if (!bestAskPtr)
            {
                break;
            }
            Order &bestAsk = *bestAskPtr;
            if (bestAsk.price() > incoming.price())
            {
                break;
            }
            int tradeQty = std::min(qty, bestAsk.quantity());
            double price = bestAsk.price();
            trades.emplace_back(models::Trade(incoming.order_id(), bestAsk.order_id(), incoming.user_id(), bestAsk.user_id(),
                                              price, tradeQty,
                                              std::chrono::system_clock::now()));
            qty -= tradeQty;
            bestAsk.setQuantity(bestAsk.quantity() - tradeQty);
            if (bestAsk.quantity() == 0)
                book.removeOrder(bestAsk);
        }
        incoming.setQuantity(qty);
        // Update the Last Traded Price (LTP) in the OrderBook if any trades occurred.
        if (!trades.empty())
            book.updateLTP(trades.back().price());
            
        return trades;
    }

    /**
     * @brief Destructor for BuyMarketStrategy.
     */
    BuyMarketStrategy::~BuyMarketStrategy() {}

} // namespace core
