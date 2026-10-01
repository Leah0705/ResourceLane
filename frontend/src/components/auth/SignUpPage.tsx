// components/SignupForm.tsx

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { KeyRound, QrCode, Copy, Check, Layers3 } from "lucide-react";
import OTPInput from "./OTPInput";
import { useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/useAuthStore";
import FingerprintJS from "@fingerprintjs/fingerprintjs";

type AuthStep = "register" | "register-qr";

const SignupForm = () => {
  const [step, setStep] = useState<AuthStep>("register");
  const [phone, setPhone] = useState("");
  const [username, setUsername] = useState("");
  const [totpCode, setTotpCode] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [secretCopied, setSecretCopied] = useState(false);

  const navigate = useNavigate();

  const signup = useAuthStore((state) => state.signup);
  const verifyTOTP = useAuthStore((state) => state.verifyTOTP);
  const tempUser = useAuthStore((state) => state.tempUser);

  // const{}

  const onBackToLanding = () => {
    navigate("/");
  };

  const getDeviceFingerprint = async () => {
    const fp = await FingerprintJS.load();
    const result = await fp.get();
    return result.visitorId;
  };

  const handleRegister = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      await signup({ phoneNumber: phone, userName: username });
      setStep("register-qr");
    } catch {
      // registration failed — stay on register step
    }

    setIsLoading(false);
  };

  const handleVerifyTOTP = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsLoading(true);

    if (totpCode.length !== 6) {
      setIsLoading(false);
      return;
    }

    try {
      const deviceFingerprint = await getDeviceFingerprint();
      await verifyTOTP(phone, totpCode, deviceFingerprint);
      navigate("/dashboard");
    } catch {
      // Keep the authenticator setup open for another attempt.
    } finally {
      setIsLoading(false);
    }
  };

  const copySecret = async () => {
    const secret = tempUser?.secretKey ?? "";
    if (secret) {
      await navigator.clipboard.writeText(secret);
      setSecretCopied(true);
      setTimeout(() => setSecretCopied(false), 2000);
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
          <p className="text-gray-600 font-light">Create your account</p>
        </div>

        <Card className="border border-gray-200 shadow-sm">
          <CardHeader className="text-center pb-4">
            <CardTitle className="text-black font-light flex items-center justify-center">
              {step === "register" && <KeyRound className="h-5 w-5 mr-2" />}
              {step === "register-qr" && <QrCode className="h-5 w-5 mr-2" />}
              {step === "register" && "Enter Details"}
              {step === "register-qr" && "Setup Authenticator"}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {step === "register" && (
              <form onSubmit={handleRegister} className="space-y-6">
                <div>
                  <Label
                    htmlFor="username"
                    className="text-gray-700 font-light"
                  >
                    Username
                  </Label>
                  <Input
                    id="username"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    required
                  />
                </div>

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
                  {isLoading ? "Processing..." : "Register"}
                </Button>
              </form>
            )}

            {step === "register-qr" && tempUser && (
              <>
                <div className="text-center mb-6">
                  <div className="bg-white p-6 border border-gray-200 rounded-lg mb-4">
                    <img
                      src={tempUser.qrCode ?? ""}
                      alt="QR Code"
                      className="w-48 h-48 mx-auto"
                    />
                  </div>

                  <div className="mb-4">
                    <Label className="text-gray-700 font-light text-sm">
                      Secret Key
                    </Label>
                    <div className="flex items-center mt-2 space-x-2">
                      <Input
                        value={tempUser.secretKey ?? ""}
                        readOnly
                        className="font-mono text-sm border-gray-200 bg-gray-50"
                      />
                      <Button
                        type="button"
                        onClick={copySecret}
                        variant="outline"
                        size="sm"
                        className="border-gray-200 hover:bg-gray-50"
                      >
                        {secretCopied ? (
                          <Check className="h-4 w-4" />
                        ) : (
                          <Copy className="h-4 w-4" />
                        )}
                      </Button>
                    </div>
                  </div>

                  <p className="text-xs text-gray-600 font-light mb-6">
                    Scan the QR code or enter the secret key manually in your
                    authenticator app
                  </p>
                </div>

                <form onSubmit={handleVerifyTOTP} className="space-y-6">
                  <div>
                    <Label className="text-gray-700 font-light">
                      Enter TOTP Code
                    </Label>
                    <div className="mt-4">
                      <OTPInput value={totpCode} onChange={setTotpCode} />
                    </div>
                  </div>

                  <Button
                    type="submit"
                    className="w-full bg-green-600 hover:bg-green-700 text-white font-light"
                    disabled={isLoading || totpCode.length !== 6}
                  >
                    {isLoading ? "Registering..." : "Complete Setup"}
                  </Button>
                </form>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
};

export default SignupForm;
