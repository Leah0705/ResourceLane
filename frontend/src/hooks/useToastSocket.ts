import { useEffect } from "react";
import { Client } from "@stomp/stompjs";
import FingerprintJS from "@fingerprintjs/fingerprintjs";
import { useAuthStore } from "../store/useAuthStore";

export const useToastSocket = (userId: number | string) => {
  const { addToast } = useAuthStore();

  useEffect(() => {
    if (!userId) return;

    let client: Client;

    const setupWebSocket = async () => {
      const fp = await FingerprintJS.load();
      const result = await fp.get();
      const deviceFingerprint = result.visitorId;

      // Append fingerprint to WebSocket URL as query param
      //TODO: Use environment variable for WebSocket URL
      // const wsUrl = `ws://localhost:8082/ws?fingerprint=${deviceFingerprint}`;
      const protocol = window.location.protocol === "https:" ? "wss" : "ws";
      const host = window.location.host; // includes domain + port
      const wsUrl = `${protocol}://${host}/ws?fingerprint=${deviceFingerprint}&userId=${userId}`;

      client = new Client({
        webSocketFactory: () => new WebSocket(wsUrl), // ✅ Use fingerprint in query param
        // debug: (str) => console.log("[STOMP - Toast]", str),
        reconnectDelay: 5000,
        onConnect: () => {
          client.subscribe(`/topic/toast/${userId}`, (message) => {
            const body = message.body;
            addToast({
              title: "From Backend",
              description: body,
            });
          });
        },
      });

      client.activate();
    };

    setupWebSocket();

    return () => {
      if (client) {
        client.deactivate();
      }
    };
  }, [userId, addToast]);
};
