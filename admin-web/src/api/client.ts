export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
interface Options extends RequestInit {
  quiet401?: boolean;
}
export function createHttpClient(
  config: { timeoutMs?: number; onUnauthorized?: () => void } = {},
) {
  let csrf: { headerName: string; token: string } | null = null;
  async function request<T>(path: string, options: Options = {}): Promise<T> {
    const { quiet401, ...init } = options;
    const method = init.method ?? "GET";
    if (!["GET", "HEAD"].includes(method) && !csrf) {
      csrf = await request("/auth/csrf", { quiet401 });
    }
    const controller = new AbortController();
    const abort = () => controller.abort(init.signal?.reason);
    if (init.signal?.aborted) abort();
    else init.signal?.addEventListener("abort", abort, { once: true });
    let timedOut = false;
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, config.timeoutMs ?? 15000);
    try {
      const headers = new Headers(init.headers);
      if (!["GET", "HEAD"].includes(method) && csrf)
        headers.set(csrf.headerName, csrf.token);
      if (init.body && !(init.body instanceof URLSearchParams))
        headers.set("Content-Type", "application/json");
      const response = await fetch("/api/v1" + path, {
        ...init,
        headers,
        credentials: "same-origin",
        signal: controller.signal,
      });
      if (!response.ok) {
        const body = (await response.json().catch(() => ({}))) as {
          code?: string;
          message?: string;
        };
        if (response.status === 401) {
          csrf = null;
          if (!quiet401) config.onUnauthorized?.();
        }
        if (response.status === 403) csrf = null;
        throw new ApiError(
          response.status,
          body.code ?? "HTTP_ERROR",
          body.message ?? "服务暂时不可用，请稍后重试。",
        );
      }
      if (response.status === 204) return undefined as T;
      return (await response.json()) as T;
    } catch (error) {
      if (error instanceof ApiError) throw error;
      if (init.signal?.aborted)
        throw new DOMException("请求已取消", "AbortError");
      if (timedOut) throw new ApiError(0, "TIMEOUT", "请求超时，请稍后重试。");
      throw new ApiError(0, "NETWORK", "无法连接服务，请检查网络后重试。");
    } finally {
      clearTimeout(timer);
      init.signal?.removeEventListener("abort", abort);
    }
  }
  return {
    request,
    resetCsrf: () => {
      csrf = null;
    },
  };
}
export const http = createHttpClient({
  onUnauthorized: () => window.dispatchEvent(new Event("session-expired")),
});
export function errorMessage(error: unknown) {
  return error instanceof ApiError
    ? error.message
    : "暂时无法完成操作，请重试。";
}
export function isUncertain(error: unknown) {
  return error instanceof ApiError && error.status === 0;
}
