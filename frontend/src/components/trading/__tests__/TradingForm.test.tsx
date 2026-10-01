import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockCreateOrder = vi.fn();
const mockAddToast = vi.fn();

vi.mock("@/store/useAuthStore", () => ({
  useAuthStore: vi.fn((selector?: (state: Record<string, unknown>) => unknown) => {
    const state = {
      createOrder: mockCreateOrder,
      isCreatingOrder: false,
      addToast: mockAddToast,
    };
    return selector ? selector(state) : state;
  }),
}));

const mockLtp = vi.fn();
vi.mock("../../../hooks/useLtp", () => ({
  useLtp: () => mockLtp(),
}));

import TradingForm from "../TradingForm";

const defaultProps = {
  userBalances: { credits: 100000, units: 50 },
};

describe("TradingForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockLtp.mockReturnValue({ price: 5000, timestamp: Date.now() });
  });

  it("renders buy/sell toggle, price, quantity inputs", () => {
    render(<TradingForm {...defaultProps} />);

    expect(screen.getByText("Place Order")).toBeInTheDocument();
    expect(screen.getAllByText("Buy Units").length).toBeGreaterThan(0);
    expect(screen.getByRole("tab", { name: /sell units/i })).toBeInTheDocument();
  });

  it("validates units quantity only accepts integers", async () => {
    const user = userEvent.setup();
    render(<TradingForm {...defaultProps} />);

    const quantityInput = screen.getByLabelText(/quantity/i);
    await user.type(quantityInput, "5a");

    expect(quantityInput).toHaveValue(5);
  });

  it("validates price is positive for limit orders", async () => {
    const user = userEvent.setup();
    render(<TradingForm {...defaultProps} />);

    const limitRadio = screen.getByLabelText(/limit order/i);
    await user.click(limitRadio);

    const priceInput = screen.getByLabelText(/price/i);
    expect(priceInput).toBeInTheDocument();
    expect(priceInput).toHaveAttribute("min", "0");
  });

  it("submits buy order with correct payload", async () => {
    mockCreateOrder.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    render(<TradingForm {...defaultProps} />);

    const quantityInput = screen.getByLabelText(/quantity/i);
    await user.type(quantityInput, "10");
    await user.type(screen.getByLabelText(/maximum price/i), "5000");

    const buyButton = screen.getByRole("button", { name: /buy units/i });
    await user.click(buyButton);

    await waitFor(() => {
      expect(mockCreateOrder).toHaveBeenCalledWith(10, 5000, "BUY", "MARKET");
    });
  });

  it("submits sell order with correct payload", async () => {
    mockCreateOrder.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    render(<TradingForm {...defaultProps} />);

    const sellTab = screen.getByText("Sell Units");
    await user.click(sellTab);

    const quantityInput = screen.getByLabelText(/quantity/i);

    await user.clear(quantityInput);
    await user.type(quantityInput, "5");

    const sellButton = screen.getByRole("button", { name: /sell units/i });
    await user.click(sellButton);

    await waitFor(() => {
      expect(mockCreateOrder).toHaveBeenCalledWith(5, 5000, "SELL", "MARKET");
    });
  });

  it("handles insufficient funds error", async () => {
    const user = userEvent.setup();
    render(
      <TradingForm
        {...defaultProps}
        userBalances={{ credits: 100, units: 0 }}
      />
    );

    const quantityInput = screen.getByLabelText(/quantity/i);
    await user.type(quantityInput, "10");
    await user.type(screen.getByLabelText(/maximum price/i), "20");

    const buyButton = screen.getByRole("button", { name: /buy units/i });
    await user.click(buyButton);

    await waitFor(() => {
      expect(mockAddToast).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Order Failed",
          description: "Insufficient funds",
        })
      );
    });
  });
});
