import type { ToastProps } from "@/components/ui/toast";
import { AxiosError, isAxiosError } from "axios";
import { useNavigate } from "react-router-dom";
import { create } from "zustand";
import { axiosInstance } from "../lib/axios.ts";

import FingerprintJS from "@fingerprintjs/fingerprintjs";

type ToasterToast = ToastProps & {
  id: string;
  title?: React.ReactNode;
  description?: React.ReactNode;
};

interface User {
  userName: string;
  phoneNumber: string;
  balances: {
    credits: number;
    units: number;
  };
  qrCode?: string;
  secretKey?: string;
}

interface SignupData {
  phoneNumber: string;
  userName: string;
}

interface LoginData {
  phone: string;
}

export interface Order {
  orderId: number;
  userId: number;
  price: number;
  quantity: number;
  side: string;
  type: string; // "market" or "limit"
  createdAt: string; // ISO date string
  cancelRequested?: boolean; // true while the matcher has not answered a cancel request
}

interface TradeHistory {
  tradeId: number;
  orderId: number;
  userId: number;
  price: number;
  quantity: number;
  side: string;
  createdAt: string;
  type: string;
}
interface tempUser {
  qrCode: string;
  secretKey: string;
}

interface AuthStore {
  authUser: User | null;
  tempUser: tempUser | null;
  userId: string | null;
  isSigningUp: boolean;
  isLoggingIn: boolean;
  isAddingCredits: boolean;
  isAddingUnits: boolean;
  isWithdrawingCredits: boolean;
  isGettingUser: boolean;
  isCreatingOrder: boolean;
  isGettingOrder: boolean;
  isGettingTradeHistory: boolean;
  toasts: ToasterToast[];
  addToast: (toast: Omit<ToasterToast, "id">) => string;
  dismissToast: (id: string) => void;
  removeToast: (id: string) => void;
  signup: (data: SignupData) => Promise<void>;
  login: (
    data: LoginData,
    navigate: ReturnType<typeof useNavigate>
  ) => Promise<void>;
  logout: () => Promise<void>;
  verifyTOTP: (
    phoneNumber: string,
    totp: string,
    deviceFingerprint: string
  ) => Promise<void>;
  addMoney: (amount: number) => Promise<void>;
  addUnits: (quantity: number) => Promise<void>;
  withdrawMoney: (amount: number) => Promise<void>;
  getWallet: () => Promise<{ credits: number; units: number } | null>;
  getUser: () => Promise<void>;
  getUserId: () => Promise<void>;
  createOrder: (
    quantity: number,
    price: number,
    side: string,
    type: string
  ) => Promise<void>;
  getOrders: () => Promise<void>;
  cancelOrder: (orderId: number) => Promise<void>;
  getTradeHistory: () => Promise<void>;
  orders: Order[];
  pastTrades: TradeHistory[];
}

const authApi = axiosInstance("auth");

export const useAuthStore = create<AuthStore>((set, get) => ({
  authUser: null,
  tempUser: null,
  userId: null,
  isSigningUp: false,
  isLoggingIn: false,
  isAddingCredits: false,
  isAddingUnits: false,
  isWithdrawingCredits: false,
  isGettingUser: false,
  isCreatingOrder: false,
  isGettingOrder: false,
  isGettingTradeHistory: false,
  toasts: [],
  orders: [],
  pastTrades: [],

  addToast: ({ title, description }) => {
    const id = Math.random().toString(36).substr(2, 9);
    set((state) => ({
      toasts: [...state.toasts, { id, title, description }],
    }));
    return id;
  },

  dismissToast: (id) => {
    set((state) => ({
      toasts: state.toasts.map((toast) =>
        toast.id === id ? { ...toast, open: false } : toast
      ),
    }));
  },

  removeToast: (id) => {
    set((state) => ({
      toasts: state.toasts.filter((toast) => toast.id !== id),
    }));
  },

  signup: async (data) => {
    set({ isSigningUp: true });
    try {
      const res = await authApi.post("/register", {
        phoneNumber: data.phoneNumber,
        userName: data.userName,
      });

      const result = res.data;

      set({
        tempUser: {
          qrCode: result.qrCode,
          secretKey: result.secretKey,
        },
      });
      get().addToast({
        title: "Success",
        description: "Account created. Set up TOTP.",
      });
    } catch (error: unknown) {
      if (isAxiosError(error)) {
        get().addToast({
          title: "Error",
          description: error.response?.data?.error || "Signup failed",
          variant: "destructive",
        });
      } else {
        get().addToast({
          title: "Error",
          description: "An unexpected error occurred during signup",
          variant: "destructive",
        });
      }
      throw error;
    } finally {
      set({ isSigningUp: false });
    }
  },

  getUser: async () => {
    set({ isGettingUser: true });
    try {
      const FingerprintJS = await import("@fingerprintjs/fingerprintjs").then(
        (m) => m.default
      );
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();

      const res = await authApi.get("/getUser", {
        headers: {
          "X-Device-Fingerprint": visitorId,
        },
      });

      if (res.data && res.data.phoneNumber && res.data.userName) {
        const walletApi = axiosInstance("wallet");
        const walletRes = await walletApi.get("/getWallet", {
          headers: {
            "X-Device-Fingerprint": visitorId,
          },
        });

        const walletData = walletRes.data;
        set({
          authUser: {
            ...res.data,
            balances: {
              credits: walletData.balance,
              units: walletData.units,
            },
          },
        });
      } else {
        set({ authUser: null });
      }

    } catch (error) {
      set({ authUser: null });
    } finally {
      set({ isGettingUser: false });
    }
  },
  getUserId: async () => {
    try {
      const FingerprintJS = await import("@fingerprintjs/fingerprintjs").then(
        (m) => m.default
      );
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();

      const res = await authApi.get("/getUserId", {
        headers: {
          "X-Device-Fingerprint": visitorId,
        },
      });
      set({ userId: res.data });
    } catch (error) {
      set({ userId: null });
    }
  },

  login: async ({ phone }: LoginData, navigate) => {
    set({ isLoggingIn: true });
    try {
      const payload = { phoneNumber: phone };
      const res = await authApi.post("/login", payload);
      const data = res.data;

      if (data.redirect === "register") {
        get().addToast({
          title: "Account Not Found",
          description: "Redirecting to registration...",
          variant: "destructive",
        });
        navigate("/signUp");
        return;
      }

      get().addToast({
        title: "Success",
        description: "User found, proceed with TOTP verification.",
      });

    } catch (error) {
      const err = error as AxiosError;
      let msg = "Something went wrong";
      if (
        err.response?.data &&
        typeof err.response.data === "object" &&
        "error" in err.response.data
      ) {
        msg = (err.response.data as { error?: string }).error || msg;
      }

      get().addToast({
        title: "Login Failed",
        description: msg,
        variant: "destructive",
      });
      throw error;
    } finally {
      set({ isLoggingIn: false });
    }
  },

  verifyTOTP: async (phoneNumber, totp, deviceFingerprint) => {
    // Format phoneNumber to 10 digits only
    const formattedPhoneNumber = phoneNumber.replace(/\D/g, "").slice(-10);
    try {
      const response = await authApi.post("/verify", {
        phoneNumber: formattedPhoneNumber,
        totp,
        deviceFingerprint,
      });

      const userInfoRes = await authApi.get("/getUser", {
        headers: {
          "X-Device-Fingerprint": deviceFingerprint,
        },
      });
      const userInfo = userInfoRes.data;

      const walletApi = axiosInstance("wallet");
      const walletRes = await walletApi.get("/getWallet", {
        headers: {
          "X-Device-Fingerprint": deviceFingerprint,
        },
      });

      const walletData = walletRes.data;
      set({
        authUser: {
          ...userInfo,
          balances: {
            credits: walletData.balance,
            units: walletData.units,
          },
        },
        userId: null,
        orders: [],
        pastTrades: [],
      });

      set({ tempUser: null });

      get().addToast({
        title: "Success",
        description: "Verification successful",
      });
    } catch (error) {
      const err = error as AxiosError;
      let msg = "Verification failed";
      if (
        err.response?.data &&
        typeof err.response.data === "object" &&
        "error" in err.response.data
      ) {
        msg = (err.response.data as { error?: string }).error || msg;
      }

      get().addToast({
        title: "Verification Failed",
        description: msg,
        variant: "destructive",
      });
      throw error;
    }
  },

  logout: async () => {
    try {
      const FingerprintJS = await import("@fingerprintjs/fingerprintjs").then(
        (m) => m.default
      );
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();

      await authApi.post(
        "/logout",
        {},
        {
          headers: {
            "X-Device-Fingerprint": visitorId,
          },
        }
      );

      set({ authUser: null, userId: null, orders: [], pastTrades: [] });

      get().addToast({
        title: "Success",
        description: "Logged out successfully",
      });
    } catch (error: unknown) {
      if (isAxiosError(error)) {
        get().addToast({
          title: "Error",
          description: error.response?.data?.message || "Logout failed",
          variant: "destructive",
        });
      } else {
        get().addToast({
          title: "Error",
          description: "An unexpected error occurred during logout",
          variant: "destructive",
        });
      }
    }
  },

  addMoney: async (amount) => {
    set({ isAddingCredits: true });
    try {
      const walletApi = axiosInstance("wallet");
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();
      await walletApi.post(
        "/addMoney",
        { amount },
        {
          headers: {
            "X-Device-Fingerprint": visitorId,
          },
        }
      );
      set((state) => ({
        authUser: {
          ...state.authUser!,
          balances: {
            ...state.authUser!.balances,
            credits: state.authUser!.balances.credits + amount,
          },
        },
      }));
      get().addToast({
        title: "Credits Funds Added",
        description: `Successfully added Cr ${amount.toFixed(2)}`,
      });
    } catch (error) {
      get().addToast({
        title: "Error",
        description: "Failed to add Credits funds",
        variant: "destructive",
      });
    } finally {
      set({ isAddingCredits: false });
    }
  },

  addUnits: async (quantity) => {
    set({ isAddingUnits: true });
    try {
      const walletApi = axiosInstance("wallet");
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();
      await walletApi.post(
        "/addUnits",
        { quantity },
        {
          headers: {
            "X-Device-Fingerprint": visitorId,
          },
        }
      );
      set((state) => ({
        authUser: {
          ...state.authUser!,
          balances: {
            ...state.authUser!.balances,
            units: state.authUser!.balances.units + quantity,
          },
        },
      }));
      get().addToast({
        title: "Units Added",
        description: `Successfully added ${quantity.toFixed(0)} units`,
      });
    } catch (error) {
      get().addToast({
        title: "Error",
        description: "Failed to add units",
        variant: "destructive",
      });
    } finally {
      set({ isAddingUnits: false });
    }
  },

  withdrawMoney: async (amount) => {
    set({ isWithdrawingCredits: true });
    try {
      const walletApi = axiosInstance("wallet");
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();
      await walletApi.post(
        "/withdrawMoney",
        { amount },
        {
          headers: {
            "X-Device-Fingerprint": visitorId,
          },
        }
      );
      set((state) => ({
        authUser: {
          ...state.authUser!,
          balances: {
            ...state.authUser!.balances,
            credits: state.authUser!.balances.credits - amount,
          },
        },
      }));
      get().addToast({
        title: "Credits Withdrawn",
        description: `Successfully withdrew Cr ${amount.toFixed(2)}`,
      });
    } catch (error) {
      if (isAxiosError(error) && error.response?.data?.error) {
          get().addToast({
            title: "Error",
            description: error.response.data.error,
            variant: "destructive",
          });
      } else {
           get().addToast({
            title: "Error",
            description: "Withdrawal failed",
            variant: "destructive",
          });
      }
    } finally {
      set({ isWithdrawingCredits: false });
    }
  },

  getWallet: async () => {
    try {
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();
      const walletApi = axiosInstance("wallet");
      const walletRes = await walletApi.get("/getWallet", {
        headers: {
          "X-Device-Fingerprint": visitorId,
        },
      });
      const walletData = walletRes.data;
      set((state) => ({
        authUser: {
          ...state.authUser!,
          balances: {
            credits: walletData.balance,
            units: walletData.units,
          },
        },
      }));
      return {
        credits: walletData.balance,
        units: walletData.units,
      };
    } catch (error) {
      return null;
    }
  },
  createOrder: async (quantity, price, side, type) => {
    set({ isCreatingOrder: true });
    try {
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();
      const tradeApi = axiosInstance("trade");
      await tradeApi.post(
        "/createOrder",
        { quantity, price, side, type },
        {
          headers: {
            "X-Device-Fingerprint": visitorId,
          },
        }
      );
      get().addToast({
        title: "Order Created",
        description: type === "MARKET" && side === "BUY"
          ? `Buy order for ${quantity} units queued with a maximum price of Cr ${price}`
          : `Successfully created ${side} order for ${quantity} at Cr ${price}`,
      });
    } catch (error) {
      get().addToast({
        title: "Error",
        description: isAxiosError(error) && error.response?.status === 409
          ? error.response.data?.error || "Insufficient available balance or units"
          : "Failed to create order",
        variant: "destructive",
      });
    } finally {
      set({ isCreatingOrder: false });
    }
  },
  getOrders: async () => {
    const accountPhone = get().authUser?.phoneNumber;
    set({ isGettingOrder: true });
    try {
      const FingerprintJS = await import("@fingerprintjs/fingerprintjs").then(
        (m) => m.default
      );
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();

      const tradeApi = axiosInstance("trade");
      const res = await tradeApi.get("/getOrders", {
        headers: {
          "X-Device-Fingerprint": visitorId,
        },
      });
      if (accountPhone && get().authUser?.phoneNumber === accountPhone) {
        set({ orders: res.data.orders });
      }
    } catch (error) {
      if (accountPhone && get().authUser?.phoneNumber === accountPhone) {
        set({ orders: [] });
      }

      //   return [];
    } finally {
      set({ isGettingOrder: false });
    }
  },
  cancelOrder: async (orderId) => {
    try {
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();
      const tradeApi = axiosInstance("trade");
      await tradeApi.post(
        "/cancelOrder",
        { orderId },
        { headers: { "X-Device-Fingerprint": visitorId } }
      );
      get().addToast({
        title: "Cancel Requested",
        description: `Cancel for order #${orderId} sent to the matching engine`,
      });
    } catch (error) {
      get().addToast({
        title: "Error",
        description:
          isAxiosError(error) && (error.response?.status === 404 || error.response?.status === 409)
            ? error.response.data?.error || "This order cannot be cancelled"
            : "Failed to cancel order",
        variant: "destructive",
      });
    }
    await get().getOrders();
  },
  getTradeHistory: async () => {
    const accountPhone = get().authUser?.phoneNumber;
    set({ isGettingTradeHistory: true });
    try {
      const FingerprintJS = await import("@fingerprintjs/fingerprintjs").then(
        (m) => m.default
      );
      const fp = await FingerprintJS.load();
      const { visitorId } = await fp.get();

      const tradeApi = axiosInstance("trade");
      const res = await tradeApi.get("/getPastTrades", {
        headers: {
          "X-Device-Fingerprint": visitorId,
        },
      });
      if (accountPhone && get().authUser?.phoneNumber === accountPhone) {
        set({ pastTrades: res.data.pastTrades });
      }
    } catch (error) {
      if (accountPhone && get().authUser?.phoneNumber === accountPhone) {
        set({ pastTrades: [] });
      }
      //   return [];
    } finally {
      set({ isGettingTradeHistory: false });
    }
  },
}));
