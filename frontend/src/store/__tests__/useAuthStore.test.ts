import { describe, it, expect, vi, beforeEach } from "vitest";
import type { NavigateFunction } from "react-router-dom";

const mockApi = vi.hoisted(() => ({ post: vi.fn(), get: vi.fn() }));

vi.mock("../../lib/axios", () => ({
  axiosInstance: vi.fn(() => mockApi),
}));
vi.mock("@fingerprintjs/fingerprintjs", () => ({
  default: {
    load: vi.fn().mockResolvedValue({
      get: vi.fn().mockResolvedValue({ visitorId: "fp123" }),
    }),
  },
}));

import { useAuthStore } from "../useAuthStore";

const mockNavigate = vi.fn();
const oldOrder = {
  orderId: 1, userId: 1, price: 12, quantity: 1,
  side: "BUY", type: "MARKET", createdAt: "2026-10-01T00:00:00Z",
};
const oldTrade = {
  tradeId: 1, orderId: 1, userId: 1, price: 12, quantity: 1,
  side: "BUY", type: "MARKET", createdAt: "2026-10-01T00:00:00Z",
};

describe("useAuthStore", () => {
  beforeEach(() => {
    useAuthStore.setState({
      authUser: null,
      tempUser: null,
      userId: null,
      isLoggingIn: false,
      isSigningUp: false,
      toasts: [],
      orders: [],
      pastTrades: [],
    });
    vi.clearAllMocks();
  });

  it("initial state is unauthenticated", () => {
    const state = useAuthStore.getState();
    expect(state.authUser).toBeNull();
    expect(state.tempUser).toBeNull();
    expect(state.isLoggingIn).toBe(false);
    expect(state.isSigningUp).toBe(false);
    expect(state.toasts).toEqual([]);
  });

  it("signIn adds a toast and throws error on failed login", async () => {
    mockApi.post.mockRejectedValueOnce({
      response: { data: { error: "Phone number not registered" } },
    });

    const state = useAuthStore.getState();
    await expect(
      state.login({ phone: "1234567890" }, mockNavigate as NavigateFunction)
    ).rejects.toThrow();

    const toasts = useAuthStore.getState().toasts;
    expect(toasts.length).toBeGreaterThan(0);
    expect(toasts[0].title).toBe("Login Failed");
    expect(toasts[0].description).toContain("Phone number not registered");
  });

  it("signIn redirects to register when account not found", async () => {
    mockApi.post.mockResolvedValueOnce({
      data: { redirect: "register" },
    });

    const state = useAuthStore.getState();
    await state.login({ phone: "1234567890" }, mockNavigate as NavigateFunction);

    expect(mockNavigate).toHaveBeenCalledWith("/signUp");
  });

  it("logout resets state", async () => {
    useAuthStore.setState({
      authUser: {
        userName: "testuser",
        phoneNumber: "1234567890",
        balances: { credits: 1000, units: 5 },
      },
      userId: "1",
      orders: [oldOrder],
      pastTrades: [oldTrade],
    });

    mockApi.post.mockResolvedValueOnce({});

    await useAuthStore.getState().logout();

    expect(useAuthStore.getState().authUser).toBeNull();
    expect(useAuthStore.getState().userId).toBeNull();
    expect(useAuthStore.getState().orders).toEqual([]);
    expect(useAuthStore.getState().pastTrades).toEqual([]);
  });

  it("verifyTOTP updates state on success", async () => {
    useAuthStore.setState({ userId: "1", orders: [oldOrder], pastTrades: [oldTrade] });
    mockApi.post.mockResolvedValueOnce({ data: {} });
    mockApi.get
      .mockResolvedValueOnce({
        data: { userName: "testuser", phoneNumber: "1234567890" },
      })
      .mockResolvedValueOnce({
        data: { balance: 5000, units: 10 },
      });

    await useAuthStore.getState().verifyTOTP("1234567890", "123456", "fp123");

    const state = useAuthStore.getState();
    expect(state.authUser).not.toBeNull();
    expect(state.authUser?.userName).toBe("testuser");
    expect(state.authUser?.balances.credits).toBe(5000);
    expect(state.authUser?.balances.units).toBe(10);
    expect(state.tempUser).toBeNull();
    expect(state.userId).toBeNull();
    expect(state.orders).toEqual([]);
    expect(state.pastTrades).toEqual([]);
  });

  it("does not show an old account's delayed trade history after switching users", async () => {
    let resolveRequest!: (value: unknown) => void;
    mockApi.get.mockImplementationOnce(() => new Promise((resolve) => {
      resolveRequest = resolve;
    }));
    useAuthStore.setState({
      authUser: { userName: "buyer", phoneNumber: "1234567890", balances: { credits: 0, units: 0 } },
    });

    const request = useAuthStore.getState().getTradeHistory();
    await vi.waitFor(() => expect(mockApi.get).toHaveBeenCalled());
    useAuthStore.setState({
      authUser: { userName: "seller", phoneNumber: "0987654321", balances: { credits: 0, units: 0 } },
      pastTrades: [],
    });
    resolveRequest({ data: { pastTrades: [oldTrade] } });
    await request;

    expect(useAuthStore.getState().pastTrades).toEqual([]);
  });

  it("verifyTOTP throws on failure", async () => {
    mockApi.post.mockRejectedValueOnce({
      response: { data: { error: "Invalid TOTP" } },
    });

    await expect(
      useAuthStore.getState().verifyTOTP("1234567890", "000000", "fp123")
    ).rejects.toThrow();
  });

  it("cancelOrder posts the order ID and refreshes open orders", async () => {
    useAuthStore.setState({ authUser: { userName: "a", phoneNumber: "1", balances: { credits: 0, units: 0 } } });
    mockApi.post.mockResolvedValueOnce({ data: { status: "Cancel request queued" } });
    mockApi.get.mockResolvedValueOnce({ data: { orders: [{ ...oldOrder, type: "LIMIT", cancelRequested: true }] } });

    await useAuthStore.getState().cancelOrder(1);

    expect(mockApi.post).toHaveBeenCalledWith(
      "/cancelOrder",
      { orderId: 1 },
      { headers: { "X-Device-Fingerprint": "fp123" } }
    );
    expect(useAuthStore.getState().orders[0].cancelRequested).toBe(true);
    expect(useAuthStore.getState().toasts.at(-1)?.title).toBe("Cancel Requested");
  });

  it("cancelOrder shows the server's reason when the order cannot be cancelled", async () => {
    useAuthStore.setState({ authUser: { userName: "a", phoneNumber: "1", balances: { credits: 0, units: 0 } } });
    const rejection = Object.assign(new Error("conflict"), {
      isAxiosError: true,
      response: { status: 409, data: { error: "Cancel already requested" } },
    });
    mockApi.post.mockRejectedValueOnce(rejection);
    mockApi.get.mockResolvedValueOnce({ data: { orders: [] } });

    await useAuthStore.getState().cancelOrder(1);

    const toast = useAuthStore.getState().toasts.at(-1);
    expect(toast?.title).toBe("Error");
    expect(toast?.description).toBe("Cancel already requested");
  });

  it("addToast adds a toast to the list", () => {
    const id = useAuthStore.getState().addToast({
      title: "Test",
      description: "Test description",
    });
    expect(id).toBeDefined();
    const state = useAuthStore.getState();
    expect(state.toasts.length).toBe(1);
    expect(state.toasts[0].title).toBe("Test");
  });

  it("dismissToast marks a toast as closed", () => {
    const id = useAuthStore.getState().addToast({
      title: "Test",
      description: "Test description",
    });
    useAuthStore.getState().dismissToast(id);
    const toasts = useAuthStore.getState().toasts;
    const toast = toasts.find((t) => t.id === id);
    expect(toast?.open).toBe(false);
  });

  it("removeToast removes a toast from the list", () => {
    const id = useAuthStore.getState().addToast({
      title: "Test",
      description: "Test description",
    });
    useAuthStore.getState().removeToast(id);
    const toasts = useAuthStore.getState().toasts;
    expect(toasts.find((t) => t.id === id)).toBeUndefined();
  });
});
