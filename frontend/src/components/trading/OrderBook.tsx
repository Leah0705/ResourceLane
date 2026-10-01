import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { BookOpen } from "lucide-react";
import { useLtp } from "../../hooks/useLtp";
import { useEffect } from "react";
import { useOrderBook } from "@/hooks/useOrderBook";
import { useToastSocket } from "@/hooks/useToastSocket";
import { useAuthStore } from "@/store/useAuthStore";

const OrderBook = () => {
  const { getUserId, userId } = useAuthStore();

  useEffect(() => {
    getUserId();
  }, [getUserId]);

  const ltp = useLtp();
  const orderBook = useOrderBook();
  useToastSocket(userId);

  return (
    <Card className="border border-gray-100 shadow-sm">
      <CardHeader className="pb-4">
        <CardTitle className="text-black font-light flex items-center text-lg">
          <BookOpen className="h-5 w-5 mr-2" />
          Order Book
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <div className="grid grid-cols-2 gap-8 px-6">
          {/* Buy Orders (Bids) - Left Side */}
          <div className="space-y-2">
            <div className="text-center pb-3 border-b border-gray-100">
              <h3 className="text-sm font-medium text-green-600">BUY ORDERS</h3>
            </div>

            {/* Header - Flipped to bring price closer to center */}
            <div className="grid grid-cols-2 gap-2 text-xs text-gray-500 font-light pb-2">
              {/* <div>Total (Credits)</div> */}
              <div className="text-center">Units</div>
              <div className="text-right">Price (Credits)</div>
            </div>

            {/* Bids - Flipped columns */}
            <div className="space-y-1">
              {orderBook?.bids?.map((bid, index) => (
                <div
                  key={index}
                  className="grid grid-cols-2 gap-2 py-1.5 text-sm hover:bg-green-50 transition-colors rounded-sm"
                >
                  {/* <div className="text-gray-600 font-mono text-sm">
                    {bid.price * bid.volume}
                  </div> */}
                  <div className="text-center text-black font-mono text-sm">
                    {bid.volume.toFixed(0)}
                  </div>
                  <div className="text-right text-green-600 font-mono text-sm">
                    {bid.price.toFixed(2)}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Sell Orders (Asks) - Right Side - Flipped */}
          <div className="space-y-2">
            <div className="text-center pb-3 border-b border-gray-100">
              <h3 className="text-sm font-medium text-red-600">SELL ORDERS</h3>
            </div>

            {/* Header - Flipped to bring price closer to center */}
            <div className="grid grid-cols-2 gap-2 text-xs text-gray-500 font-light pb-2">
              <div>Price (Credits)</div>
              <div className="text-center">Units</div>
              {/* <div className="text-right">Total (Credits)</div> */}
            </div>

            {/* Asks - Flipped */}
            <div className="space-y-1">
              {orderBook?.asks?.map((ask, index) => (
                <div
                  key={index}
                  className="grid grid-cols-2 gap-2 py-1.5 text-sm hover:bg-red-50 transition-colors rounded-sm"
                >
                  <div className="text-red-600 font-mono text-sm">
                    {ask.price.toFixed(2)}
                  </div>
                  <div className="text-center text-black font-mono text-sm">
                    {ask.volume.toFixed(0)}
                  </div>
                  {/* <div className="text-right text-gray-600 font-mono text-sm">
                    {ask.price * ask.volume}
                  </div> */}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Current Price - More sleek styling */}
        <div className="px-6 py-6 bg-gray-50 border-t border-gray-200 mt-6">
          <div className="text-center">
            {/* <div className="text-black font-normal text-2xl tracking-wide">${currentPrice.toFixed(2)}</div> */}
            <div className="text-black font-normal text-2xl tracking-wide">
              {ltp?.price != null ? `Cr ${ltp.price.toFixed(2)}` : "Awaiting first match"}
            </div>
            <div className="text-xs text-gray-500 font-light mt-1 uppercase tracking-wider">
              Last Traded Price
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};

export default OrderBook;
