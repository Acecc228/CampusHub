import { afterEach, describe, expect, it, vi } from "vitest";
import { createHttpClient } from "./client";

afterEach(() => vi.unstubAllGlobals());
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });

describe("HTTP 安全与恢复契约（隔离单元测试，非真实联调）", () => {
  it("写请求先获取 CSRF，携带 Cookie 和服务端指定 header", async () => {
    const calls: RequestInit[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init: RequestInit) => {
        calls.push(init);
        return url.endsWith("/auth/csrf")
          ? json({ headerName: "X-CSRF-TOKEN", token: "unit-token" })
          : json({ id: 1 });
      }),
    );
    await createHttpClient().request("/manage/activities", {
      method: "POST",
      body: "{}",
    });
    expect(calls).toHaveLength(2);
    expect(calls[1]?.credentials).toBe("same-origin");
    expect(new Headers(calls[1]?.headers).get("X-CSRF-TOKEN")).toBe(
      "unit-token",
    );
  });
  it("401 通知失效；登录失败不触发全局跳转", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        json({ code: "UNAUTHORIZED", message: "请先认证" }, 401),
      ),
    );
    const expired = vi.fn();
    const client = createHttpClient({ onUnauthorized: expired });
    await expect(client.request("/auth/me")).rejects.toMatchObject({
      status: 401,
    });
    expect(expired).toHaveBeenCalledOnce();
    await expect(
      client.request("/auth/me", { quiet401: true }),
    ).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledOnce();
  });
  it("409 保留业务信息且绝不自动重试写入", async () => {
    const fetcher = vi.fn(async (url: string) =>
      url.endsWith("/auth/csrf")
        ? json({ headerName: "X-CSRF-TOKEN", token: "t" })
        : json({ code: "ACTIVITY_CONFLICT", message: "版本已变更" }, 409),
    );
    vi.stubGlobal("fetch", fetcher);
    await expect(
      createHttpClient().request("/manage/activities/1", {
        method: "PUT",
        body: "{}",
      }),
    ).rejects.toMatchObject({ status: 409, code: "ACTIVITY_CONFLICT" });
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
  it("网络失败不暴露底层异常", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockRejectedValue(new TypeError("internal secret diagnostic")),
    );
    await expect(
      createHttpClient().request("/activities"),
    ).rejects.toMatchObject({
      code: "NETWORK",
      message: "无法连接服务，请检查网络后重试。",
    });
  });
  it("请求超时有上限", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(
        (_url: string, init: RequestInit) =>
          new Promise((_resolve, reject) =>
            init.signal?.addEventListener("abort", () =>
              reject(init.signal?.reason),
            ),
          ),
      ),
    );
    await expect(
      createHttpClient({ timeoutMs: 15 }).request("/activities"),
    ).rejects.toMatchObject({ code: "TIMEOUT" });
  });
});
