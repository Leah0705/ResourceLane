import { Button } from "@/components/ui/button";
import { Layers3, Shield, TrendingUp, Users } from "lucide-react";
import { useNavigate } from "react-router-dom";

interface LandingPageProps {
  onLoginClick: () => void;
  onSignupClick: () => void;
}

const LandingPage = () => {
  const navigate = useNavigate();

  const onLoginClick = () => {
    navigate("/login");
  };
  const onSignupClick = () => {
    navigate("/signUp");
  };

  return (
    <div className="min-h-screen bg-white">
      {/* Navigation */}
      <nav className="bg-white border-b border-gray-200">
        <div className="container mx-auto px-6">
          <div className="flex items-center justify-between h-16">
            <div className="flex items-center">
              <Layers3 className="h-10 w-10 mr-3 text-amber-600" />
              <h1 className="text-xl font-light text-black">ResourceLane</h1>
            </div>

            <div className="flex items-center space-x-4">
              <Button
                onClick={onLoginClick}
                variant="outline"
                className="border-gray-200 text-gray-600 hover:bg-gray-50 font-light"
              >
                Login
              </Button>
              <Button
                onClick={onSignupClick}
                className="bg-black hover:bg-gray-800 text-white font-light"
              >
                Sign Up
              </Button>
            </div>
          </div>
        </div>
      </nav>

      {/* Hero Section */}
      <section className="py-24 bg-white">
        <div className="container mx-auto px-6 text-center">
          <h1 className="text-5xl font-light text-black mb-6">
            Match Standardized Resource Units
          </h1>
          <p className="text-xl text-gray-600 font-light mb-12 max-w-3xl mx-auto">
            Place buy and sell orders for a shared pool of resource units.
            Follow the order book, matched trades, and demo-credit balances
            from one dashboard.
          </p>
          <Button
            onClick={onLoginClick}
            size="lg"
            className="bg-black hover:bg-gray-800 text-white font-light text-lg px-8 py-4"
          >
            Open Dashboard
          </Button>
          <img
            src="/resource-flow.png"
            alt="Resource units meeting in matching lanes and flowing into ledger stacks"
            width={1672}
            height={941}
            className="mt-16 mx-auto w-full max-w-6xl rounded-2xl"
          />
        </div>
      </section>

      {/* Features Section */}
      <section className="py-20 bg-gray-50">
        <div className="container mx-auto px-6">
          <h2 className="text-3xl font-light text-black text-center mb-16">
            Why Choose ResourceLane?
          </h2>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-12">
            <div className="text-center">
              <div className="bg-white p-6 rounded-lg shadow-minimal mb-6 w-20 h-20 mx-auto flex items-center justify-center">
                <Shield className="h-10 w-10 text-black" />
              </div>
              <h3 className="text-xl font-normal text-black mb-4">
                Secure Authentication
              </h3>
              <p className="text-gray-600 font-light">
                TOTP verification protects sign-in alongside the account session.
              </p>
            </div>

            <div className="text-center">
              <div className="bg-white p-6 rounded-lg shadow-minimal mb-6 w-20 h-20 mx-auto flex items-center justify-center">
                <TrendingUp className="h-10 w-10 text-black" />
              </div>
              <h3 className="text-xl font-normal text-black mb-4">
                Live Order Matching
              </h3>
              <p className="text-gray-600 font-light">
                Submit limit or market orders and follow the current order book
                and recent matches.
              </p>
            </div>

            <div className="text-center">
              <div className="bg-white p-6 rounded-lg shadow-minimal mb-6 w-20 h-20 mx-auto flex items-center justify-center">
                <Users className="h-10 w-10 text-black" />
              </div>
              <h3 className="text-xl font-normal text-black mb-4">
                Traceable Settlement
              </h3>
              <p className="text-gray-600 font-light">
                Review matched orders and the corresponding changes to each
                demo wallet.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20 bg-white">
        <div className="container mx-auto px-6 text-center">
          <h2 className="text-3xl font-light text-black mb-6">
            Ready to Try ResourceLane?
          </h2>
          <p className="text-lg text-gray-600 font-light mb-8">
            Create a demo account, add resource units or credits, and place an order.
          </p>
          <div className="flex justify-center space-x-4">
            <Button
              onClick={onSignupClick}
              size="lg"
              className="bg-black hover:bg-gray-800 text-white font-light"
            >
              Create Account
            </Button>
            <Button
              onClick={onLoginClick}
              variant="outline"
              size="lg"
              className="border-gray-200 text-gray-600 hover:bg-gray-50 font-light"
            >
              Login
            </Button>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="bg-gray-50 py-12 border-t border-gray-200">
        <div className="container mx-auto px-6">
          <div className="text-center">
            <div className="flex items-center justify-center mb-6">
              <Layers3 className="h-8 w-8 mr-3 text-amber-600" />
              <h3 className="text-xl font-light text-black">ResourceLane</h3>
            </div>
            <p className="text-gray-600 font-light mb-6">
              Resource matching and settlement prototype
            </p>
            <p className="text-sm text-gray-500 font-light">
              Local demo with simulated resource units and credits.
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
};

export default LandingPage;
