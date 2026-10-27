import { afterEach, expect, it, vi } from "vitest";
import { authApi } from "./auth";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});
it("uses production login by default and refreshes CSRF after session rotation", async () => {
  vi.stubEnv("VITE_DEMO_LOGIN", "false");
  const calls: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      calls.push(url);
      const body = url.endsWith("/csrf")
        ? { headerName: "X-CSRF", token: "test" }
        : { id: 1, roles: ["ADMIN"] };
      return new Response(JSON.stringify(body), { status: 200 });
    }),
  );
  await authApi.login("admin", "secret");
  await authApi.logout();
  expect(calls).toEqual([
    "/api/v1/auth/csrf",
    "/api/v1/auth/login",
    "/api/v1/auth/me",
    "/api/v1/auth/csrf",
    "/api/v1/auth/logout",
  ]);
});
it("uses demo endpoint only when explicitly enabled in development", async () => {
  vi.stubEnv("VITE_DEMO_LOGIN", "true");
  const calls: string[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      calls.push(url);
      return new Response(
        JSON.stringify(
          url.endsWith("/csrf")
            ? { headerName: "X-CSRF", token: "test" }
            : { id: 1, roles: ["ADMIN"] },
        ),
        { status: 200 },
      );
    }),
  );
  await authApi.login("demo", "secret");
  expect(calls).toContain("/api/v1/auth/demo-login");
});
