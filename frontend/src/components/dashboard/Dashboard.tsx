import { useState, useEffect } from "react";
import Navbar from "./Navbar";
import Portfolio from "./Portfolio";
import TradingInterface from "../trading/TradingInterface";
import WalletManager from "./WalletManager";
import { useAuthStore } from "@/store/useAuthStore";

const Dashboard = () => {
  const { authUser } = useAuthStore();

  const [activeTab, setActiveTab] = useState<"trading" | "wallet">("trading");
  const [userBalances, setUserBalances] = useState(authUser?.balances);

  useEffect(() => {
    setUserBalances(authUser?.balances ?? { credits: 0, units: 0 });
  }, [authUser?.balances]);

  return (
    <div className="min-h-screen bg-white">
      <Navbar
        user={{ ...authUser, balances: userBalances }}
      />

      <div className="container mx-auto px-6 py-8">
        <Portfolio balances={userBalances} />

        <div className="mt-8">
          <div className="flex space-x-2 mb-8">
            <button
              onClick={() => setActiveTab("trading")}
              className={`px-6 py-3 rounded-md font-light transition-colors ${
                activeTab === "trading"
                  ? "bg-black text-white"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              Trading
            </button>
            <button
              onClick={() => setActiveTab("wallet")}
              className={`px-6 py-3 rounded-md font-light transition-colors ${
                activeTab === "wallet"
                  ? "bg-black text-white"
                  : "bg-gray-100 text-gray-600 hover:bg-gray-200"
              }`}
            >
              Wallet
            </button>
          </div>

          {activeTab === "trading" ? (
            <TradingInterface userBalances={userBalances} />
          ) : (
            <WalletManager balances={userBalances} />
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
