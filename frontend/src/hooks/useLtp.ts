import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";
import FingerprintJS from "@fingerprintjs/fingerprintjs";

interface LtpData {
  price: number;
  timestamp: number;
}

export const useLtp = () => {
  const [ltp, setLtp] = useState<LtpData>({
    price: 1,
    timestamp: Date.now(),
  });

  useEffect(() => {
    let client: Client;

    const setupWebSocket = async () => {
      const fp = await FingerprintJS.load();
      const result = await fp.get();
      const deviceFingerprint = result.visitorId;

      // Create raw WebSocket with fingerprint in query params

      //TODO: Use environment variable for WebSocket URL
      // const wsUrl = `ws://localhost:8082/ws?fingerprint=${deviceFingerprint}`;
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      const host = window.location.host; // includes domain + port
      const wsUrl = `${protocol}://${host}/ws?fingerprint=${deviceFingerprint}`;

      client = new Client({
        webSocketFactory: () => new WebSocket(wsUrl), // ✅ Custom WebSocket
        // debug: (str) => console.log("[STOMP]", str),
        reconnectDelay: 5000,
        onConnect: () => {
          client.subscribe("/topic/ltp", (message) => {
            const body: LtpData = JSON.parse(message.body);
            setLtp(body);
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

  return ltp;
};
