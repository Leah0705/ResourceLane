import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor, act } from "@testing-library/react";

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

import { useLtp } from "../useLtp";

describe("useLtp", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("connects to WebSocket on mount and returns initial LTP", async () => {
    const { result } = renderHook(() => useLtp());

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    expect(result.current).toBeDefined();
    expect(result.current.price).toBe(1);
    expect(typeof result.current.timestamp).toBe("number");
  });

  it("updates LTP data on message", async () => {
    const { result } = renderHook(() => useLtp());

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    const subscribeCallback = mockSubscribe.mock.calls[0]?.[1];
    expect(subscribeCallback).toBeDefined();

    act(() => {
      subscribeCallback({
        body: JSON.stringify({ price: 2500, timestamp: Date.now() }),
      });
    });

    await waitFor(() => {
      expect(result.current.price).toBe(2500);
    });
  });

  it("disconnects on unmount", async () => {
    const { unmount } = renderHook(() => useLtp());

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    unmount();
    expect(mockDeactivate).toHaveBeenCalled();
  });
});
