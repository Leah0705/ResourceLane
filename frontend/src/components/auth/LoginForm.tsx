import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Layers3, KeyRound, Shield } from "lucide-react";
import OTPInput from "./OTPInput";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/useAuthStore";

type AuthStep = "phone" | "totp";

const LoginForm = () => {
  const { login, verifyTOTP } = useAuthStore();
  const [step, setStep] = useState<AuthStep>("phone");
  const [phone, setPhone] = useState("");
  const [totp, setTotp] = useState("");
  const [isLoading, setIsLoading] = useState(false);

  const navigate = useNavigate();
  const onBackToLanding = () => {
    navigate("/");
  };

  const handlePhoneSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    try {
      await login({ phone }, navigate);
      setStep("totp");
    } catch (error) {
      // Stay on phone step
    } finally {
      setIsLoading(false);
    }
  };

  const handleTOTPSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    if (totp.length !== 6) {
      setIsLoading(false);
      return;
    }

    try {
      const FingerprintJS = await import("@fingerprintjs/fingerprintjs");
      const fp = await FingerprintJS.load();
      const result = await fp.get();
      const deviceFingerprint = result.visitorId;

      await verifyTOTP(phone, totp, deviceFingerprint);
      navigate("/dashboard");
    } catch (error) {
      console.error("Verification error:", error);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-white p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-12">
          <button
            onClick={onBackToLanding}
            className="flex items-center justify-center mb-6 mx-auto hover:opacity-80 transition-opacity"
          >
            <Layers3 className="h-14 w-14 mr-3 text-amber-600" />
            <h1 className="text-4xl font-light text-black mr-5">ResourceLane</h1>
          </button>
          <p className="text-gray-600 font-light">Welcome back</p>
        </div>

        <Card className="border border-gray-200 shadow-sm">
          <CardHeader className="text-center pb-4">
            <CardTitle className="text-black font-light flex items-center justify-center">
              {step === "phone" && <KeyRound className="h-5 w-5 mr-2" />}
              {step === "totp" && <Shield className="h-5 w-5 mr-2" />}
              {step === "phone" && "Enter Demo Account ID"}
              {step === "totp" && "Enter TOTP Code"}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {step === "phone" && (
              <form onSubmit={handlePhoneSubmit} className="space-y-6">
                <div>
                  <Label htmlFor="phone" className="text-gray-700 font-light">
                    Demo Account ID (10 digits)
                  </Label>
                  <div className="mt-2">
                    <Input
                      id="phone"
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]{10}"
                      minLength={10}
                      maxLength={10}
                      value={phone}
                      onChange={(e) =>
                        setPhone(e.target.value.replace(/\D/g, "").slice(0, 10))
                      }
                      className="flex-1 border-gray-200 focus:border-black"
                      placeholder="0123456789"
                      required
                    />
                  </div>
                </div>

                <Button
                  type="submit"
                  className="w-full bg-black hover:bg-gray-800 text-white font-light"
                  disabled={isLoading}
                >
                  {isLoading ? "Verifying..." : "Next"}
                </Button>

                <Button
                  type="button"
                  onClick={onBackToLanding}
                  variant="outline"
                  className="w-full border-gray-200 text-gray-600 hover:bg-gray-50 font-light"
                >
                  Back to Home
                </Button>
              </form>
            )}

            {step === "totp" && (
              <form onSubmit={handleTOTPSubmit} className="space-y-6">
                <div>
                  <Label className="text-gray-700 font-light">
                    Enter TOTP Code
                  </Label>
                  <div className="mt-4">
                    <OTPInput value={totp} onChange={setTotp} />
                  </div>
                  <p className="text-xs text-gray-500 mt-2 font-light text-center">
                    Enter the 6-digit code from your authenticator app
                  </p>
                </div>

                <div className="flex space-x-3">
                  <Button
                    type="button"
                    onClick={() => setStep("phone")}
                    variant="outline"
                    className="flex-1 border-gray-200 text-gray-600 hover:bg-gray-50 font-light"
                  >
                    Back
                  </Button>
                  <Button
                    type="submit"
                    className="flex-1 bg-black hover:bg-gray-800 text-white font-light"
                    disabled={isLoading || totp.length !== 6}
                  >
                    {isLoading ? "Authenticating..." : "Login"}
                  </Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default LoginForm;
