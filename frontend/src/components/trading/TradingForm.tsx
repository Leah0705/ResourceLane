import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { TrendingUp } from "lucide-react";
import { useAuthStore } from "@/store/useAuthStore";
import { useLtp } from "../../hooks/useLtp";

interface TradingFormProps {
  userBalances: {
    credits: number;
    units: number;
  };
}

const TradingForm = ({ userBalances }: TradingFormProps) => {
  const { createOrder, isCreatingOrder, addToast } = useAuthStore();

  const ltp = useLtp();
  const currentPrice = ltp?.price ?? 0;

  const [buyQuantity, setBuyQuantity] = useState("");
  const [sellQuantity, setSellQuantity] = useState("");
  const [buyOrderType, setBuyOrderType] = useState("market");
  const [sellOrderType, setSellOrderType] = useState("market");
  const [buyPrice, setBuyPrice] = useState("");
  const [sellPrice, setSellPrice] = useState("");

  const handleBuy = async () => {
    const quantity = parseFloat(buyQuantity);
    if (!quantity || quantity <= 0) {
      addToast({
        title: "Order Failed",
        description: "Quantity must be greater than 0",
      });
      setBuyQuantity("");
      setBuyPrice("");
      return;
    }

    const price = parseFloat(buyPrice);
    if (!Number.isFinite(price) || price <= 0) {
      addToast({
        title: "Order Failed",
        description: "Enter a price greater than 0",
      });
      setBuyQuantity("");
      setBuyPrice("");
      return;
    }

    const total = quantity * price;

    if (total > userBalances.credits) {
      addToast({
        title: "Order Failed",
        description: "Insufficient funds",
      });
      setBuyQuantity("");
      setBuyPrice("");
      return;
    }

    const side = "BUY";
    const type = buyOrderType === "limit" ? "LIMIT" : "MARKET";

    createOrder(quantity, price, side, type);

    setBuyQuantity("");
    setBuyPrice("");
  };

  const handleSell = async () => {
    const quantity = parseFloat(sellQuantity);
    if (!quantity || quantity <= 0) {
      addToast({
        title: "Order Failed",
        description: "Quantity must be greater than 0",
      });
      setSellQuantity("");
      setSellPrice("");
      return;
    }

    if (quantity > userBalances.units) {
      addToast({
        title: "Order Failed",
        description: "Insufficient units",
      });
      setSellQuantity("");
      setSellPrice("");
      return;
    }

    const price =
      sellOrderType === "limit" ? parseFloat(sellPrice) : currentPrice;
    if (!price || price <= 0) {
      addToast({
        title: "Order Failed",
        description: sellOrderType === "limit" ? "Price must be greater than 0" : "No last matched price; place a limit order first",
      });
      setSellQuantity("");
      setSellPrice("");
      return;
    }

    const side = "SELL";
    const type = sellOrderType === "limit" ? "LIMIT" : "MARKET";

    createOrder(quantity, price, side, type);

    setSellQuantity("");
    setSellPrice("");
  };

  return (
    <Card className="border border-gray-100 shadow-sm">
      <CardHeader className="pb-4">
        <CardTitle className="text-black font-light flex items-center text-lg">
          <TrendingUp className="h-5 w-5 mr-2" />
          Place Order
        </CardTitle>
      </CardHeader>
      <CardContent>
        <Tabs defaultValue="BUY" className="w-full">
          <TabsList className="grid w-full grid-cols-2 bg-gray-100">
            <TabsTrigger
              value="BUY"
              className="text-gray-700 data-[state=active]:bg-green-600 data-[state=active]:text-white"
            >
              Buy Units
            </TabsTrigger>
            <TabsTrigger
              value="SELL"
              className="text-gray-700 data-[state=active]:bg-red-600 data-[state=active]:text-white"
            >
              Sell Units
            </TabsTrigger>
          </TabsList>

          <TabsContent value="BUY" className="space-y-5 mt-6">
            <div className="space-y-3">
              <Label className="text-gray-700 font-light text-sm">
                Order Type
              </Label>
              <RadioGroup
                value={buyOrderType}
                onValueChange={setBuyOrderType}
                className="flex space-x-6"
              >
                <div className="flex items-center space-x-2">
                  <RadioGroupItem
                    value="market"
                    id="buy-market"
                    className="border-gray-300"
                  />
                  <Label
                    htmlFor="buy-market"
                    className="text-sm font-light cursor-pointer"
                  >
                    Market Order
                  </Label>
                </div>
                <div className="flex items-center space-x-2">
                  <RadioGroupItem
                    value="limit"
                    id="buy-limit"
                    className="border-gray-300"
                  />
                  <Label
                    htmlFor="buy-limit"
                    className="text-sm font-light cursor-pointer"
                  >
                    Limit Order
                  </Label>
                </div>
              </RadioGroup>
            </div>

            {(
              <div className="space-y-2">
                <Label
                  htmlFor="buy-price"
                  className="text-gray-700 font-light text-sm"
                >
                  {buyOrderType === "market" ? "Maximum price (Credits)" : "Price (Credits)"}
                </Label>
                <Input
                  id="buy-price"
                  type="number"
                  value={buyPrice}
                  onChange={(e) => setBuyPrice(e.target.value)}
                  className="bg-white border-gray-300 text-black focus:border-green-500 focus:ring-green-500"
                  placeholder={currentPrice.toFixed(2)}
                  min="0"
                  step="0.01"
                />
                {buyOrderType === "market" && (
                  <p className="text-xs text-gray-500">Fills immediately at or below this price; any remainder is cancelled.</p>
                )}
              </div>
            )}

            <div className="space-y-2">
              <Label
                htmlFor="buy-quantity"
                className="text-gray-700 font-light text-sm"
              >
                Quantity (units)
              </Label>
              <Input
                id="buy-quantity"
                type="number"
                value={buyQuantity}
                // onChange={(e) => setBuyQuantity(e.target.value)}
                onChange={(e) => {
                  const value = e.target.value;
                  if (/^\d*$/.test(value)) {
                    setBuyQuantity(value);
                  }
                }}
                className="bg-white border-gray-300 text-black focus:border-green-500 focus:ring-green-500"
                placeholder="0"
                min="0"
                step="1"
              />
            </div>

            <div className="bg-gray-50 p-4 rounded-lg space-y-3 text-sm">
              <div className="flex justify-between text-gray-600">
                <span>Price:</span>
                <span className="text-black font-mono">
                  Cr{" "}
                  {buyPrice || "0.00"}
                </span>
              </div>
              <div className="flex justify-between text-gray-600 border-t border-gray-200 pt-3">
                <span>Total:</span>
                <span className="text-black font-mono font-medium">
                  Cr{" "}
                  {buyQuantity
                    ? (
                        parseFloat(buyQuantity) *
                        (parseFloat(buyPrice) || 0)
                      ).toFixed(2)
                    : "0.00"}
                </span>
              </div>
              <div className="text-xs text-gray-500">
                Available: Cr {(userBalances?.credits ?? 0).toLocaleString()}
              </div>
            </div>

            <Button
              onClick={handleBuy}
              disabled={
                isCreatingOrder ||
                !buyQuantity ||
                !buyPrice
              }
              className="w-full bg-green-600 hover:bg-green-700 text-white font-light h-11"
            >
              {isCreatingOrder ? "Submitting..." : "Buy Units"}
            </Button>
          </TabsContent>

          <TabsContent value="SELL" className="space-y-5 mt-6">
            <div className="space-y-3">
              <Label className="text-gray-700 font-light text-sm">
                Order Type
              </Label>
              <RadioGroup
                value={sellOrderType}
                onValueChange={setSellOrderType}
                className="flex space-x-6"
              >
                <div className="flex items-center space-x-2">
                  <RadioGroupItem
                    value="market"
                    id="sell-market"
                    className="border-gray-300"
                  />
                  <Label
                    htmlFor="sell-market"
                    className="text-sm font-light cursor-pointer"
                  >
                    Market Order
                  </Label>
                </div>
                <div className="flex items-center space-x-2">
                  <RadioGroupItem
                    value="limit"
                    id="sell-limit"
                    className="border-gray-300"
                  />
                  <Label
                    htmlFor="sell-limit"
                    className="text-sm font-light cursor-pointer"
                  >
                    Limit Order
                  </Label>
                </div>
              </RadioGroup>
            </div>

            {sellOrderType === "limit" && (
              <div className="space-y-2">
                <Label
                  htmlFor="sell-price"
                  className="text-gray-700 font-light text-sm"
                >
                  Price (Credits)
                </Label>
                <Input
                  id="sell-price"
                  type="number"
                  value={sellPrice}
                  onChange={(e) => setSellPrice(e.target.value)}
                  className="bg-white border-gray-300 text-black focus:border-red-500 focus:ring-red-500"
                  placeholder={currentPrice.toFixed(2)}
                  min="0"
                  step="0.01"
                />
              </div>
            )}

            <div className="space-y-2">
              <Label
                htmlFor="sell-quantity"
                className="text-gray-700 font-light text-sm"
              >
                Quantity (units)
              </Label>
              <Input
                id="sell-quantity"
                type="number"
                value={sellQuantity}
                // onChange={(e) => setSellQuantity(e.target.value)}
                onChange={(e) => {
                  const value = e.target.value;
                  if (/^\d*$/.test(value)) {
                    setSellQuantity(value);
                  }
                }}
                className="bg-white border-gray-300 text-black focus:border-red-500 focus:ring-red-500"
                placeholder="0"
                min="0"
                step="1"
              />
            </div>

            <div className="bg-gray-50 p-4 rounded-lg space-y-3 text-sm">
              <div className="flex justify-between text-gray-600">
                <span>Price:</span>
                <span className="text-black font-mono">
                  Cr{" "}
                  {sellOrderType === "limit"
                    ? sellPrice || currentPrice.toFixed(2)
                    : currentPrice.toFixed(2)}
                </span>
              </div>
              <div className="flex justify-between text-gray-600 border-t border-gray-200 pt-3">
                <span>Total:</span>
                <span className="text-black font-mono font-medium">
                  Cr{" "}
                  {sellQuantity
                    ? (
                        parseFloat(sellQuantity) *
                        (sellOrderType === "limit"
                          ? parseFloat(sellPrice) || currentPrice
                          : currentPrice)
                      ).toFixed(2)
                    : "0.00"}
                </span>
              </div>
              <div className="text-xs text-gray-500">
                Available: {(userBalances?.units ?? 0).toFixed(0)} units
              </div>
            </div>

            <Button
              onClick={handleSell}
              disabled={
                isCreatingOrder ||
                !sellQuantity ||
                (sellOrderType === "limit" && !sellPrice)
              }
              className="w-full bg-red-600 hover:bg-red-700 text-white font-light h-11"
            >
              {isCreatingOrder ? "Submitting..." : "Sell Units"}
            </Button>
          </TabsContent>
        </Tabs>
      </CardContent>
    </Card>
  );
};

export default TradingForm;
