import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import FingerprintJS from "@fingerprintjs/fingerprintjs";

interface OrderLevel {
  price: number;
  volume: number;
}

interface OrderBook {
  asks: OrderLevel[];
  bids: OrderLevel[];
}

export const useOrderBook = () => {
  const [orderBook, setOrderBook] = useState<OrderBook | null>(null);

  useEffect(() => {
    let client: Client;

    const setupWebSocket = async () => {
      // Get device fingerprint
      const fp = await FingerprintJS.load();
      const result = await fp.get();
      const deviceFingerprint = result.visitorId;

      // Append fingerprint to WebSocket URL as query param
      //TODO: Use environment variable for WebSocket URL
      // const wsUrl = `ws://localhost:8082/ws?fingerprint=${deviceFingerprint}`;
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      const host = window.location.host; // includes domain + port
      const wsUrl = `${protocol}://${host}/ws?fingerprint=${deviceFingerprint}`;

      client = new Client({
        webSocketFactory: () => new WebSocket(wsUrl), // ✅ Use custom WebSocket
        // debug: (str) => console.log("[STOMP - OrderBook]", str),
        reconnectDelay: 5000,
        onConnect: () => {
          client.subscribe("/topic/orderbook", (message) => {
            const body: OrderBook = JSON.parse(message.body);
            setOrderBook(body);
          });
        },
      });

      client.activate();
    };

    setupWebSocket();

    return () => {
      if (client) client.deactivate();
    };
  }, []);

  return orderBook;
};
