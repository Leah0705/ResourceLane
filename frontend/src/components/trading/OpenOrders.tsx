import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/store/useAuthStore";
import { ListOrdered } from "lucide-react";
import { useEffect } from "react";

const OpenOrders = () => {
  const { getOrders, orders, cancelOrder } = useAuthStore();

  useEffect(() => {
    const intervalId = setInterval(() => {
      getOrders();
    }, 3000);
    getOrders();
    return () => clearInterval(intervalId);
  }, [getOrders]);

  return (
    <Card className="border border-gray-100 shadow-sm">
      <CardHeader className="pb-4">
        <CardTitle className="text-black font-light flex items-center text-lg">
          <ListOrdered className="h-5 w-5 mr-2" />
          Open Orders
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <div className="grid grid-cols-4 gap-2 px-6 pt-2 text-xs text-gray-500 font-light">
          <div>Side</div>
          <div className="text-right">Quantity</div>
          <div className="text-right">Price</div>
          <div className="text-right" />
        </div>
        <div className="max-h-96 overflow-y-auto">
          {!orders || orders.length === 0 ? (
            <div className="px-6 py-8 text-center text-gray-500 font-light">
              No open orders
            </div>
          ) : (
            orders.map((order) => (
              <div
                key={order.orderId}
                className="grid grid-cols-4 gap-2 px-6 py-3 text-sm items-center border-b border-gray-100"
              >
                <div className="space-y-1">
                  <div className={order.side === "BUY" ? "text-green-600" : "text-red-600"}>
                    {order.side} {order.type}
                  </div>
                  <div className="text-gray-500 text-xs font-light">
                    {new Date(order.createdAt).toLocaleString()}
                  </div>
                </div>
                <div className="text-right font-mono font-light">{order.quantity} units</div>
                <div className="text-right font-mono font-light">Cr {order.price.toFixed(2)}</div>
                <div className="text-right">
                  {order.type === "LIMIT" &&
                    (order.cancelRequested ? (
                      <span className="text-xs text-gray-500">Cancelling…</span>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => cancelOrder(order.orderId)}
                        aria-label={`Cancel order ${order.orderId}`}
                      >
                        Cancel
                      </Button>
                    ))}
                </div>
              </div>
            ))
          )}
        </div>
      </CardContent>
    </Card>
  );
};

export default OpenOrders;
