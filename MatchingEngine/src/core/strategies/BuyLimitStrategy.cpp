#include "core/strategies/BuyLimitStrategy.hpp"
#include "core/OrderBook.hpp"
#include "core/Order.hpp" // Include for full definition of Order
#include <chrono>
#include <algorithm>

namespace core
{

    /**
     * @brief Constructor for BuyLimitStrategy.
     */
    BuyLimitStrategy::BuyLimitStrategy() {}

    /**
     * @brief Matches an incoming Buy Limit order against existing Sell orders in the OrderBook.
     * 
     * Iterates while the incoming order has remaining quantity and there are matching 
     * sell orders (asks) in the book. A match occurs if the best ask price is less than
     * or equal to the incoming buy order's limit price. 
     * Generates Trade objects for executed matches.
     * 
     * @param incoming The new Buy Limit order to be matched.
     * @param book Reference to the OrderBook containing resting orders.
     * @return std::vector<models::Trade> List of trades resulting from the matches.
     */
    std::vector<models::Trade> BuyLimitStrategy::match(
        Order &incoming, OrderBook &book)
    {

        std::vector<models::Trade> trades;
        int qty = incoming.quantity();
        double limit = incoming.price();

        while (qty > 0)
        {
            auto bestAskPtr = book.getBestAsk();
            if (!bestAskPtr)
                break;
            auto &bestAsk = *bestAskPtr;
            if (bestAsk.price() > limit)
                break;

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
        // Update the remaining quantity of the incoming order.
        incoming.setQuantity(qty);
        
        // Update the Last Traded Price (LTP) in the OrderBook if any trades occurred.
        if (!trades.empty())
            book.updateLTP(trades.back().price());
            
        // If the incoming order still has remaining quantity, add it to the OrderBook.
        if (incoming.quantity() > 0)
            book.addOrder(incoming);

        return trades;
    }

    /**
     * @brief Destructor for BuyLimitStrategy.
     */
    BuyLimitStrategy::~BuyLimitStrategy() {}

} // namespace core
