#include "core/strategies/SellLimitStrategy.hpp"
#include "core/OrderBook.hpp"
#include "core/Order.hpp" // Include for full definition of Order
#include <chrono>
#include <algorithm>

namespace core
{

    /**
     * @brief Constructor for SellLimitStrategy.
     */
    SellLimitStrategy::SellLimitStrategy() {}

    /**
     * @brief Matches an incoming Sell Limit order against existing Buy orders in the OrderBook.
     * 
     * Iterates while the incoming order has remaining quantity and there are matching 
     * buy orders (bids) in the book. A match occurs if the best bid price is greater than
     * or equal to the incoming sell order's limit price. 
     * Generates Trade objects for executed matches.
     * 
     * @param incoming The new Sell Limit order to be matched.
     * @param book Reference to the OrderBook containing resting orders.
     * @return std::vector<models::Trade> List of trades resulting from the matches.
     */
    std::vector<models::Trade> SellLimitStrategy::match(
        Order &incoming, OrderBook &book)
    {
        std::vector<models::Trade> trades;
        int qty = incoming.quantity();
        double limit = incoming.price();

        while (qty > 0)
        {
            auto bestBidPtr = book.getBestBid();
            if (!bestBidPtr)
                break;
            auto &bestBid = *bestBidPtr;
            if (bestBid.price() < limit)
                break;

            int tradeQty = std::min(qty, bestBid.quantity());
            double price = bestBid.price();

            trades.push_back(models::Trade(bestBid.order_id(), incoming.order_id(), bestBid.user_id(), incoming.user_id(),
                                           price, tradeQty,
                                            std::chrono::system_clock::now()));


            qty -= tradeQty;
            bestBid.setQuantity(bestBid.quantity() - tradeQty);

            if (bestBid.quantity() == 0)
                book.removeOrder(bestBid);
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
     * @brief Destructor for SellLimitStrategy.
     */
    SellLimitStrategy::~SellLimitStrategy() {}

} // namespace core
