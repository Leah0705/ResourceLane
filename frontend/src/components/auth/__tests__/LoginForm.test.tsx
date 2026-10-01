import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BrowserRouter } from "react-router-dom";

const mockLogin = vi.fn();
const mockVerifyTOTP = vi.fn();
const mockNavigate = vi.fn();

vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock("@/store/useAuthStore", () => ({
  useAuthStore: vi.fn((selector?: (state: Record<string, unknown>) => unknown) => {
    const state = {
      login: mockLogin,
      verifyTOTP: mockVerifyTOTP,
      authUser: null,
    };
    return selector ? selector(state) : state;
  }),
}));

import LoginForm from "../LoginForm";

const renderLoginForm = () =>
  render(
    <BrowserRouter>
      <LoginForm />
    </BrowserRouter>
  );

describe("LoginForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders login form with account ID input and submit button", () => {
    renderLoginForm();
    expect(screen.getByText("Enter Demo Account ID")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("0123456789")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /next/i })).toBeInTheDocument();
  });

  it("handles input changes", async () => {
    const user = userEvent.setup();
    renderLoginForm();

    const phoneInput = screen.getByPlaceholderText("0123456789");
    await user.type(phoneInput, "9876543210");

    expect(phoneInput).toHaveValue("9876543210");
  });

  it("submits form on button click and shows TOTP step", async () => {
    mockLogin.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    renderLoginForm();

    const phoneInput = screen.getByPlaceholderText("0123456789");
    await user.type(phoneInput, "9876543210");

    const nextButton = screen.getByRole("button", { name: /next/i });
    await user.click(nextButton);

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith(
        { phone: "9876543210" },
        expect.any(Function)
      );
    });

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /enter totp code/i })).toBeInTheDocument();
    });
  });

  it("displays error message on failed login", async () => {
    mockLogin.mockRejectedValueOnce(new Error("Login failed"));

    const user = userEvent.setup();
    renderLoginForm();

    const phoneInput = screen.getByPlaceholderText("0123456789");
    await user.type(phoneInput, "9876543210");

    const nextButton = screen.getByRole("button", { name: /next/i });
    await user.click(nextButton);

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalled();
    });

    expect(screen.getByText("Enter Demo Account ID")).toBeInTheDocument();
  });

  it("navigates to dashboard on successful TOTP verification", async () => {
    mockLogin.mockResolvedValueOnce(undefined);
    mockVerifyTOTP.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    renderLoginForm();

    const phoneInput = screen.getByPlaceholderText("0123456789");
    await user.type(phoneInput, "9876543210");

    const nextButton = screen.getByRole("button", { name: /next/i });
    await user.click(nextButton);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /enter totp code/i })).toBeInTheDocument();
    });

    const otpInputs = screen.getAllByRole("textbox");
    for (let i = 0; i < otpInputs.length; i++) {
      await user.type(otpInputs[i], String(i + 1));
    }

    const loginButton = screen.getByRole("button", { name: /login/i });
    await user.click(loginButton);

    await waitFor(() => {
      expect(mockVerifyTOTP).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith("/dashboard");
    });
  });
});
