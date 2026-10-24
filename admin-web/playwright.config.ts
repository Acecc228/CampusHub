import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60000,
  expect: { timeout: 10000 },
  outputDir: "../.local/admin-e2e",
  reporter: [
    ["list"],
    ["json", { outputFile: "../docs/verification/m1.4-a-e2e.json" }],
  ],
  use: {
    baseURL: "http://127.0.0.1:5173",
    channel: "msedge",
    actionTimeout: 10000,
    viewport: { width: 1440, height: 960 },
    trace: "off",
    screenshot: "off",
    video: "off",
  },
});
