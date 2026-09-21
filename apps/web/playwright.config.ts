import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  use: {
    baseURL: "http://127.0.0.1:3100",
    trace: "on-first-retry",
    viewport: { width: 1280, height: 900 },
  },
  webServer: {
    command: "npx cross-env KJX_API_MODE=mock NEXT_PUBLIC_ENABLE_MOCK_TOOLS=true next build && npx cross-env KJX_API_MODE=mock NEXT_PUBLIC_ENABLE_MOCK_TOOLS=true next start --port 3100 --hostname 127.0.0.1",
    url: "http://127.0.0.1:3100",
    reuseExistingServer: false,
    timeout: 120_000,
    env: {
      ...process.env,
      KJX_API_MODE: "mock",
      NEXT_PUBLIC_ENABLE_MOCK_TOOLS: "true",
    },
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"], channel: "chrome" },
    },
    {
      name: "mobile-chrome",
      use: { ...devices["Pixel 5"], channel: "chrome" },
    },
  ],
});
