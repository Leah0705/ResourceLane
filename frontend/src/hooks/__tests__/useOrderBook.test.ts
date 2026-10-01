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

import { useOrderBook } from "../useOrderBook";

describe("useOrderBook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("connects to WebSocket on mount and returns null initially", async () => {
    const { result } = renderHook(() => useOrderBook());

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    expect(result.current).toBeNull();
  });

  it("updates order book data on message", async () => {
    const { result } = renderHook(() => useOrderBook());

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    const subscribeCallback = mockSubscribe.mock.calls[0]?.[1];
    expect(subscribeCallback).toBeDefined();
    expect(mockSubscribe.mock.calls[0]?.[0]).toBe("/topic/orderbook");

    const mockData = {
      asks: [{ price: 2050, volume: 10 }],
      bids: [{ price: 2040, volume: 5 }],
    };

    act(() => {
      subscribeCallback({ body: JSON.stringify(mockData) });
    });

    await waitFor(() => {
      expect(result.current).not.toBeNull();
      expect(result.current?.asks).toHaveLength(1);
      expect(result.current?.bids).toHaveLength(1);
      expect(result.current?.asks[0].price).toBe(2050);
    });
  });

  it("disconnects on unmount", async () => {
    const { unmount } = renderHook(() => useOrderBook());

    await waitFor(() => {
      expect(mockSubscribe.mock.calls.length).toBeGreaterThan(0);
    });

    unmount();
    expect(mockDeactivate).toHaveBeenCalled();
  });
});
