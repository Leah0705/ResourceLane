import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BrowserRouter } from "react-router-dom";

const mockSignup = vi.fn();
const mockVerifyTOTP = vi.fn();
const mockNavigate = vi.fn();
let mockTempUserVal: { qrCode: string; secretKey: string } | null = null;

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock("@/store/useAuthStore", () => {
  return {
    useAuthStore: vi.fn((selector?: (state: Record<string, unknown>) => unknown) => {
      const state = {
        signup: mockSignup,
        verifyTOTP: mockVerifyTOTP,
        tempUser: mockTempUserVal,
        authUser: null,
      };
      return selector ? selector(state) : state;
    }),
  };
});

vi.mock("@fingerprintjs/fingerprintjs", () => ({
  default: {
    load: vi.fn().mockResolvedValue({
      get: vi.fn().mockResolvedValue({ visitorId: "mock-fp" }),
    }),
  },
}));

import SignUpPage from "../SignUpPage";

const renderSignUpPage = () =>
  render(
    <BrowserRouter>
      <SignUpPage />
    </BrowserRouter>
  );

describe("SignUpPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockTempUserVal = null;
  });

  it("renders registration form with username and account ID fields", () => {
    renderSignUpPage();
    expect(screen.getByText("Enter Details")).toBeInTheDocument();
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText("0123456789")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /register/i })).toBeInTheDocument();
  });

  it("validates required fields exist", () => {
    renderSignUpPage();
    expect(screen.getByLabelText(/username/i)).toBeRequired();
    expect(screen.getByPlaceholderText("0123456789")).toBeRequired();
  });

  it("shows QR code after successful registration", async () => {
    mockTempUserVal = { qrCode: "data:image/png;base64,mock", secretKey: "MOCKSECRET123" };
    mockSignup.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    renderSignUpPage();

    await user.type(screen.getByLabelText(/username/i), "testuser");
    await user.type(screen.getByPlaceholderText("0123456789"), "9876543210");
    await user.click(screen.getByRole("button", { name: /register/i }));

    await waitFor(() => {
      expect(mockSignup).toHaveBeenCalledWith({ phoneNumber: "9876543210", userName: "testuser" });
    });
  });

  it("handles registration and updates step", async () => {
    mockSignup.mockResolvedValueOnce(undefined);
    mockTempUserVal = { qrCode: "data:image/png;base64,mock", secretKey: "MOCKSECRET123" };

    const user = userEvent.setup();
    renderSignUpPage();

    await user.type(screen.getByLabelText(/username/i), "newuser");
    await user.type(screen.getByPlaceholderText("0123456789"), "5551234567");
    await user.click(screen.getByRole("button", { name: /register/i }));

    await waitFor(() => { expect(mockSignup).toHaveBeenCalled(); });
    await waitFor(() => { expect(screen.getByText("Setup Authenticator")).toBeInTheDocument(); });
  });

  it("handles registration errors", async () => {
    mockSignup.mockRejectedValueOnce(new Error("Registration failed"));

    const user = userEvent.setup();
    renderSignUpPage();

    await user.type(screen.getByLabelText(/username/i), "newuser");
    await user.type(screen.getByPlaceholderText("0123456789"), "5551234567");
    await user.click(screen.getByRole("button", { name: /register/i }));

    await waitFor(() => { expect(mockSignup).toHaveBeenCalled(); });
    expect(screen.getByText("Enter Details")).toBeInTheDocument();
  });
});
