import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BrowserRouter } from "react-router-dom";

const mockAuthUser = vi.hoisted(() => ({
  userName: "testuser",
  phoneNumber: "9876543210",
  balances: { credits: 50000, units: 25 },
}));

vi.mock("@/store/useAuthStore", () => ({
  useAuthStore: vi.fn(() => ({ authUser: mockAuthUser })),
}));

vi.mock("../Navbar", () => ({
  default: () => <div data-testid="navbar">Navbar Mock</div>,
}));

vi.mock("../Portfolio", () => ({
  default: () => <div data-testid="portfolio">Portfolio Mock</div>,
}));

vi.mock("../../trading/TradingInterface", () => ({
  default: () => (
    <div data-testid="trading-interface">TradingInterface Mock</div>
  ),
}));

vi.mock("../WalletManager", () => ({
  default: () => (
    <div data-testid="wallet-manager">WalletManager Mock</div>
  ),
}));

import Dashboard from "../Dashboard";

const renderDashboard = () =>
  render(
    <BrowserRouter>
      <Dashboard />
    </BrowserRouter>
  );

describe("Dashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders Navbar and Portfolio", () => {
    renderDashboard();

    expect(screen.getByTestId("navbar")).toBeInTheDocument();
    expect(screen.getByTestId("portfolio")).toBeInTheDocument();
  });

  it("renders Trading tab by default", () => {
    renderDashboard();

    expect(screen.getByTestId("trading-interface")).toBeInTheDocument();
  });

  it("switches to Wallet tab on button click", async () => {
    const user = userEvent.setup();
    renderDashboard();

    expect(screen.getByTestId("trading-interface")).toBeInTheDocument();

    const walletButton = screen.getByText("Wallet");
    await user.click(walletButton);

    expect(screen.getByTestId("wallet-manager")).toBeInTheDocument();
    expect(
      screen.queryByTestId("trading-interface")
    ).not.toBeInTheDocument();
  });

  it("renders trading and wallet tab buttons", () => {
    renderDashboard();

    expect(screen.getByText("Trading")).toBeInTheDocument();
    expect(screen.getByText("Wallet")).toBeInTheDocument();
  });
});
