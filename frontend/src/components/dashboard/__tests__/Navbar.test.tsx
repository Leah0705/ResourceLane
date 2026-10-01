import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const mockLogout = vi.fn();

vi.mock("@/store/useAuthStore", () => ({
  useAuthStore: vi.fn((selector?: (state: Record<string, unknown>) => unknown) => {
    const state = { logout: mockLogout };
    return selector ? selector(state) : state;
  }),
}));

import Navbar from "../Navbar";

const mockUser = {
  userName: "testuser",
  phoneNumber: "9876543210",
  balances: { credits: 50000, units: 25 },
};

describe("Navbar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the brand name", () => {
    render(<Navbar user={mockUser} />);
    expect(screen.getByText("ResourceLane")).toBeInTheDocument();
  });

  it("shows user info when authenticated", () => {
    render(<Navbar user={mockUser} />);

    expect(screen.getByText("testuser")).toBeInTheDocument();
    expect(screen.getByText("(9876543210)")).toBeInTheDocument();
    expect(screen.getByText("Cr 50,000")).toBeInTheDocument();
    expect(screen.getByText("25 units")).toBeInTheDocument();
  });

  it("shows logout button when authenticated", () => {
    render(<Navbar user={mockUser} />);

    expect(
      screen.getByRole("button", { name: /logout/i })
    ).toBeInTheDocument();
  });

  it("calls logout when logout button is clicked", async () => {
    const user = userEvent.setup();
    render(<Navbar user={mockUser} />);

    const logoutButton = screen.getByRole("button", { name: /logout/i });
    await user.click(logoutButton);

    expect(mockLogout).toHaveBeenCalledTimes(1);
  });

  it("formats Credits balance with locale string", () => {
    const richUser = {
      ...mockUser,
      balances: { credits: 1000000, units: 100 },
    };
    render(<Navbar user={richUser} />);

    expect(screen.getByText("Cr 1,000,000")).toBeInTheDocument();
  });
});
