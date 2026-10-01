import "@testing-library/jest-dom";
import { vi } from "vitest";

const mockFpLoad = vi.fn().mockResolvedValue({
  get: vi.fn().mockResolvedValue({ visitorId: "mock-fingerprint" }),
});

vi.mock("@fingerprintjs/fingerprintjs", () => ({
  default: { load: mockFpLoad },
  load: mockFpLoad,
}));
