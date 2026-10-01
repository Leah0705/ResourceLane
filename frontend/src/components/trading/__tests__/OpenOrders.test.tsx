import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockGetOrders = vi.fn();
const mockCancelOrder = vi.fn();
let mockOrders: unknown[] = [];

vi.mock("@/store/useAuthStore", () => ({
  useAuthStore: vi.fn(() => ({
    getOrders: mockGetOrders,
    cancelOrder: mockCancelOrder,
    orders: mockOrders,
  })),
}));

import OpenOrders from "../OpenOrders";

const order = (overrides: Record<string, unknown>) => ({
  orderId: 1,
  userId: 7,
  price: 10.25,
  quantity: 3,
  side: "BUY",
  type: "LIMIT",
  createdAt: "2026-10-01T00:00:00Z",
  ...overrides,
});

describe("OpenOrders", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockOrders = [];
  });

  it("shows an empty state and loads orders on mount", () => {
    render(<OpenOrders />);

    expect(screen.getByText("No open orders")).toBeInTheDocument();
    expect(mockGetOrders).toHaveBeenCalled();
  });

  it("cancels a resting limit order", async () => {
    mockOrders = [order({ orderId: 42 })];
    const user = userEvent.setup();
    render(<OpenOrders />);

    await user.click(screen.getByRole("button", { name: "Cancel order 42" }));

    expect(mockCancelOrder).toHaveBeenCalledWith(42);
  });

  it("offers no cancel for market orders or orders already being cancelled", () => {
    mockOrders = [
      order({ orderId: 5, type: "MARKET" }),
      order({ orderId: 6, cancelRequested: true }),
    ];
    render(<OpenOrders />);

    expect(screen.queryByRole("button", { name: /cancel order/i })).not.toBeInTheDocument();
    expect(screen.getByText("Cancelling…")).toBeInTheDocument();
  });
});
