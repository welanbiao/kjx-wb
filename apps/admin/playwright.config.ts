import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [["list"]],
  use: {
    baseURL: "http://127.0.0.1:3310",
    trace: "on-first-retry",
    viewport: { width: 1280, height: 900 },
  },
  webServer: {
    command: "npm run build && npx vite preview --port 3310 --host 127.0.0.1",
    url: "http://127.0.0.1:3310",
    reuseExistingServer: false,
    timeout: 120_000,
    env: {
      ...process.env,
      VITE_API_MODE: "mock",
    },
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
