import { http } from "./client";
import type { Identity } from "./types";
export const authApi = {
  me: () => http.request<Identity>("/auth/me", { quiet401: true }),
  async login(username: string, password: string) {
    http.resetCsrf();
    await http.request<Identity>(
      import.meta.env.DEV && import.meta.env.VITE_DEMO_LOGIN === "true"
        ? "/auth/demo-login"
        : "/auth/login",
      {
        method: "POST",
        body: new URLSearchParams({ username, password }),
        quiet401: true,
      },
    );
    http.resetCsrf();
    return authApi.me();
  },
  async logout() {
    await http.request<void>("/auth/logout", { method: "POST" });
    http.resetCsrf();
  },
};
