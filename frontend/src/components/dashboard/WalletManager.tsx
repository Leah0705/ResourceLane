import { useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuthStore } from "@/store/useAuthStore";
import { Plus, Coins, Layers3 } from "lucide-react";

interface WalletManagerProps {
  balances: {
    credits: number;
    units: number;
  };
}

const WalletManager = ({ balances }: WalletManagerProps) => {
  const authStore = useAuthStore();
  const [creditsAmount, setCreditsAmount] = useState("");
  const [unitsAmount, setUnitsAmount] = useState("");
  const [withdrawAmount, setWithdrawAmount] = useState("");
  const [isAddingCredits, setIsAddingCredits] = useState(false);
  const [isAddingUnits, setIsAddingUnits] = useState(false);
  const [isWithdrawingCredits, setIsWithdrawingCredits] = useState(false);

  // Helper to handle both Credits and Units addition
  const processAddition = async (
    type: "credits" | "units",
    amountStr: string,
    setAmount: (v: string) => void
  ) => {
    const amount = parseFloat(amountStr);
    if (!amount || amount <= 0) return;

    if (type === "credits") {
      setIsAddingCredits(true);
      await authStore.addMoney(amount);
      setIsAddingCredits(false);
    } else {
      setIsAddingUnits(true);
      await authStore.addUnits(amount);
      setIsAddingUnits(false);
    }

    setAmount("");
  };

  const processWithdrawal = async () => {
    const amount = parseFloat(withdrawAmount);
    if (!amount || amount <= 0) return;

    setIsWithdrawingCredits(true);
    await authStore.withdrawMoney(amount);
    setWithdrawAmount("");
    setIsWithdrawingCredits(false);
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
      <Card className="border border-gray-100 shadow-sm">
        <CardHeader>
          <CardTitle className="text-black font-light flex items-center text-lg">
            <Coins className="h-5 w-5 mr-2 text-lime-500" />
            Add Demo Credits
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <Label htmlFor="credits-amount" className="text-gray-600 font-light">
              Amount (Credits)
            </Label>
            <Input
              id="credits-amount"
              type="number"
              value={creditsAmount}
              onChange={(e) => setCreditsAmount(e.target.value)}
              className="bg-white border-gray-200 text-black font-light"
              placeholder="0.00"
              min="0"
              step="0.01"
            />
          </div>
          <div className="text-sm text-gray-500 font-light">
            Current Balance: Cr {balances.credits.toLocaleString()}
          </div>
          <Button
            onClick={() => processAddition("credits", creditsAmount, setCreditsAmount)}
            disabled={isAddingCredits || !creditsAmount}
            className="w-full bg-green-600 hover:bg-green-700 text-white border-0 shadow-sm font-light"
          >
            <Plus className="h-4 w-4 mr-2" />
            {isAddingCredits ? "Processing..." : "Add Credits"}
          </Button>
        </CardContent>
      </Card>

      <Card className="border border-gray-100 shadow-sm">
        <CardHeader>
          <CardTitle className="text-black font-light flex items-center text-lg">
            <Layers3 className="h-6 w-6 mr-3 text-amber-600" />
            Add Units
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <Label htmlFor="units-amount" className="text-gray-600 font-light">
              Quantity (units)
            </Label>
            <Input
              id="units-amount"
              type="number"
              value={unitsAmount}
              onChange={(e) => setUnitsAmount(e.target.value)}
              className="bg-white border-gray-200 text-black font-light"
              placeholder="0"
              min="1"
              step="1"
            />
          </div>
          <div className="text-sm text-gray-500 font-light">
            Current Holdings: {balances.units.toFixed(0)} units
          </div>
          <Button
            onClick={() => processAddition("units", unitsAmount, setUnitsAmount)}
            disabled={isAddingUnits || !unitsAmount}
            className="w-full bg-amber-600 hover:bg-amber-700 text-white border-0 shadow-sm font-light"
          >
            <Plus className="h-4 w-4 mr-2" />
            {isAddingUnits ? "Processing..." : "Add Units"}
          </Button>
        </CardContent>
      </Card>

      <Card className="border border-gray-100 shadow-sm">
        <CardHeader>
          <CardTitle className="text-black font-light flex items-center text-lg">
            <Coins className="h-5 w-5 mr-2 text-lime-500" />
            Withdraw Demo Credits
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div>
            <Label
              htmlFor="withdraw-amount"
              className="text-gray-600 font-light"
            >
              Amount (Credits)
            </Label>
            <Input
              id="withdraw-amount"
              type="number"
              value={withdrawAmount}
              onChange={(e) => setWithdrawAmount(e.target.value)}
              className="bg-white border-gray-200 text-black font-light"
              placeholder="0.00"
              min="0"
              step="0.01"
            />
          </div>
          <div className="text-sm text-gray-500 font-light">
            Current Balance: Cr {balances.credits.toLocaleString()}
          </div>
          <Button
            onClick={processWithdrawal}
            disabled={isWithdrawingCredits || !withdrawAmount}
            className="w-full bg-red-600 hover:bg-red-700 text-white border-0 shadow-sm font-light"
          >
            <Plus className="h-4 w-4 mr-2" />
            {isWithdrawingCredits ? "Processing..." : "Withdraw Credits"}
          </Button>
        </CardContent>
      </Card>
    </div>
  );
};

export default WalletManager;
