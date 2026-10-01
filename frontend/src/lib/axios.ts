import axios from "axios";

const isDev = import.meta.env.MODE === "development";

// Map each service to its base URL
const SERVICE_URLS = {
  auth: isDev ? "http://localhost:8080/api/auth" : "/api/auth",
  wallet: isDev ? "http://localhost:8081/api/wallet" : "/api/wallet",
  trade: isDev ? "http://localhost:8082/api/trade" : "/api/trade",
};

// Factory function to get an axios instance for a specific service
export const axiosInstance = (service: keyof typeof SERVICE_URLS) => {
  return axios.create({
    baseURL: SERVICE_URLS[service],
    withCredentials: true,
  });
};
