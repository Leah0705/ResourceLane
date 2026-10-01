import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";

let currentAddToast = vi.fn();
const mockSubscribe = vi.fn();
const mockDeactivate = vi.fn();

function MockClient(this: { activate: ReturnType<typeof vi.fn>; deactivate: typeof mockDeactivate; subscribe: typeof mockSubscribe }, config: { onConnect?: () => void }) {
  this.activate = vi.fn(() => {
    if (typeof config.onConnect === "function") config.onConnect();
  });
  this.deactivate = mockDeactivate;
  this.subscribe = mockSubscribe;
}

vi.mock("@stomp/stompjs", () => ({ Client: MockClient }));

vi.mock("../../store/useAuthStore", () => ({
  useAuthStore: vi.fn((selector?: (state: Record<string, unknown>) => unknown) => {
    const state = { addToast: currentAddToast };
    return selector ? selector(state) : state;
  }),
}));

import { useToastSocket } from "../useToastSocket";

describe("useToastSocket", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    currentAddToast = vi.fn();
  });

  it("does not connect when userId is falsy", () => {
    renderHook(() => useToastSocket(0));
    expect(mockSubscribe).not.toHaveBeenCalled();
  });

  it("connects to WebSocket on mount with userId", async () => {
    renderHook(() => useToastSocket(42));

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    expect(mockSubscribe.mock.calls[0]?.[0]).toBe("/topic/toast/42");
  });

  it("shows toast notifications on messages", async () => {
    renderHook(() => useToastSocket(42));

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    const subscribeCallback = mockSubscribe.mock.calls[0]?.[1];
    subscribeCallback({ body: "Test toast" });

    expect(currentAddToast).toHaveBeenCalledWith({
      title: "From Backend",
      description: "Test toast",
    });
  });

  it("disconnects on unmount", async () => {
    const { unmount } = renderHook(() => useToastSocket(42));

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    unmount();
    expect(mockDeactivate).toHaveBeenCalled();
  });
});
