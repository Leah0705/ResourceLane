
import { useState, useRef, useEffect } from "react";

interface OTPInputProps {
  value: string;
  onChange: (value: string) => void;
  length?: number;
}

const OTPInput = ({ value, onChange, length = 6 }: OTPInputProps) => {
  const [otp, setOtp] = useState<string[]>(new Array(length).fill(""));
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    if (value) {
      const otpArray = value.split("").slice(0, length);
      while (otpArray.length < length) {
        otpArray.push("");
      }
      setOtp(otpArray);
    }
  }, [value, length]);

  const handleChange = (index: number, val: string) => {
    if (isNaN(Number(val))) return;

    const newOtp = [...otp];
    newOtp[index] = val.substring(val.length - 1);
    setOtp(newOtp);

    const otpValue = newOtp.join("");
    onChange(otpValue);

    // Move to next input if current field is filled
    if (val && index < length - 1 && inputRefs.current[index + 1]) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleClick = (index: number) => {
    inputRefs.current[index]?.setSelectionRange(1, 1);

    // If current input is empty, focus on the first empty input
    if (otp[index] === "") {
      const firstEmptyIndex = otp.findIndex(val => val === "");
      if (firstEmptyIndex !== -1 && firstEmptyIndex !== index) {
        inputRefs.current[firstEmptyIndex]?.focus();
      }
    }
  };

  const handleKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === "Backspace" && !otp[index] && index > 0 && inputRefs.current[index - 1]) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  return (
    <div className="flex justify-center space-x-3">
      {otp.map((value, index) => (
        <input
          key={index}
          ref={el => inputRefs.current[index] = el}
          type="text"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="\d{1}"
          maxLength={1}
          className="w-12 h-12 text-center text-2xl font-normal border border-gray-200 rounded-md focus:border-black focus:ring-1 focus:ring-black focus:outline-none bg-white"
          value={value}
          onChange={e => handleChange(index, e.target.value)}
          onClick={() => handleClick(index)}
          onKeyDown={e => handleKeyDown(index, e)}
        />
      ))}
    </div>
  );
};

export default OTPInput;
