import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TrendingUp, Layers3, Coins } from "lucide-react";
import { useLtp } from "../../hooks/useLtp";

interface PortfolioProps {
  balances: {
    credits: number;
    units: number;
  };
}

const Portfolio = ({ balances }: PortfolioProps) => {
  const ltp = useLtp();
  const unitsPrice = ltp?.price;
  const totalValue = unitsPrice == null ? null : (balances?.credits ?? 0) + (balances?.units ?? 0) * unitsPrice;
  const unitsValue = unitsPrice == null ? null : (balances?.units ?? 0) * unitsPrice;

  return (
    <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
      <Card className="border border-gray-100 shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-normal text-gray-600">
            Estimated Account Value
          </CardTitle>
          <TrendingUp className="h-4 w-4 text-green-600" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-light text-black">
            {totalValue == null ? "Awaiting first match" : `Cr ${totalValue.toLocaleString()}`}
          </div>
          <p className="text-xs text-slate-600 mt-1 font-light">
            Demo credits plus units at the last matched price
          </p>
        </CardContent>
      </Card>

      <Card className="border border-gray-100 shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-normal text-gray-600">
            Credits Balance
          </CardTitle>
          <Coins className="h-5 w-5 mr-2 text-lime-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-light text-black">
            Cr {(balances?.credits ?? 0).toLocaleString()}
          </div>
          <p className="text-xs text-gray-500 mt-1 font-light">
            Demo balance available for orders
          </p>
        </CardContent>
      </Card>

      <Card className="border border-gray-100 shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-normal text-gray-600">
            Units Holdings
          </CardTitle>
          <Layers3 className="h-6 w-6 mr-3 text-amber-600" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-light text-black">
            {(balances?.units ?? 0).toFixed(0)} units
          </div>
          <p className="text-xs text-gray-500 mt-1 font-light">
            {unitsValue == null ? "No last matched price" : `Cr ${unitsValue.toLocaleString()} value`}
          </p>
        </CardContent>
      </Card>

      <Card className="border border-gray-100 shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
          <CardTitle className="text-sm font-normal text-gray-600">
            Last Matched Price
          </CardTitle>

          <TrendingUp className="h-4 w-4 text-yellow-500" />
        </CardHeader>
        <CardContent>
          <div className="text-2xl font-light text-black">
            {unitsPrice == null ? "—" : `Cr ${unitsPrice.toFixed(2)}`}
          </div>
          <p className="text-xs text-slate-600 mt-1 font-light">
            Based on last traded price
          </p>
        </CardContent>
      </Card>
    </div>
  );
};

export default Portfolio;
