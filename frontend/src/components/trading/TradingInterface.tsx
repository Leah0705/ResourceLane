import OpenOrders from "./OpenOrders";
import OrderBook from "./OrderBook";
import TradingForm from "./TradingForm";
import TradeHistory from "./TradeHistory";

interface TradingInterfaceProps {
  userBalances: {
    credits: number;
    units: number;
  };
}

const TradingInterface = ({ userBalances }: TradingInterfaceProps) => (
  <div className="grid grid-cols-1 xl:grid-cols-2 gap-8">
    <OrderBook />
    <TradingForm userBalances={userBalances} />
    <OpenOrders />
    <TradeHistory />
  </div>
);

export default TradingInterface;
