import { http } from "./transport";
import { ApiError } from "./client";
import { Identity } from "./types";
import { registrationActions } from "./api";
let identity: Identity | null = null;
let generation = 0;
export const session = {
  get user() {
    return identity;
  },
  get generation() {
    return generation;
  },
  invalidate() {
    identity = null;
    generation++;
    registrationActions.clear();
    for (const page of getCurrentPages())
      if (page.route !== "pages/login/index") {
        page.setData({
          user: null,
          items: [],
          rows: [],
          activity: null,
          state: "login",
          busy: false,
          error: "",
          total: 0,
          feedback: "",
          more: false,
          busyId: 0,
          uncertain: false,
          loadingMore: false,
        });
      }
  },
  async me() {
    const user = await http.request<Identity>("/auth/me");
    if (!user.roles.includes("STUDENT"))
      throw new ApiError(
        403,
        "STUDENT_ONLY",
        "此入口仅面向学生，请退出后使用学生账号登录。",
      );
    identity = user;
    return user;
  },
  async login(username: string, password: string) {
    http.clear();
    session.invalidate();
    await http.request(
      "/auth/demo-login",
      "POST",
      `username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`,
      true,
    );
    try {
      return await session.me();
    } catch (error) {
      try {
        await http.request("/auth/logout", "POST");
      } finally {
        http.clear();
        session.invalidate();
      }
      throw error;
    }
  },
  async loginWechat(code: string) {
    http.clear();
    session.invalidate();
    await http.request("/auth/wechat-login", "POST", { code });
    try {
      return await session.me();
    } catch (error) {
      if (error instanceof ApiError && error.code === "STUDENT_ONLY") {
        try { await http.request("/auth/logout", "POST"); } finally { http.clear(); session.invalidate(); }
      }
      throw error;
    }
  },
  async logout() {
    // Leave credentials in memory on network failure so the user can retry server-side revocation.
    try {
      await http.request("/auth/logout", "POST");
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error;
    }
    http.clear();
    session.invalidate();
  },
};
http.onUnauthorized = () => session.invalidate();
