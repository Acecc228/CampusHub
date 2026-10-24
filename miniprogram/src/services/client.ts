export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
export interface Response {
  statusCode: number;
  data: unknown;
  header: Record<string, unknown>;
  cookies?: string[];
}
export interface Request {
  url: string;
  method: "GET" | "POST";
  data?: unknown;
  header: Record<string, string>;
  timeout: number;
}
export type Transport = (input: Request) => Promise<Response>;
interface Csrf {
  headerName: string;
  token: string;
}
// The sole API origin is deliberately loopback. This build cannot be used on a real device.
export const API_ORIGIN = "http://127.0.0.1:8080/api/v1";
export function createClient(transport: Transport) {
  let cookie = "";
  let csrf: Csrf | null = null;
  let epoch = 0;
  let queue: Promise<unknown> = Promise.resolve();
  function clear() {
    cookie = "";
    csrf = null;
    epoch++;
  }
  function absorb(response: Response) {
    const raw = Object.entries(response.header || {}).find(
      ([key]) => key.toLowerCase() === "set-cookie",
    )?.[1];
    const cookies = response.cookies?.length
      ? response.cookies
      : typeof raw === "string"
        ? raw.split(/,(?=\s*[^;,=\s]+=)/)
        : [];
    for (const value of cookies) {
      const match = /^\s*JSESSIONID=([^;\s,]*)/i.exec(value);
      if (!match) continue;
      if (!match[1] || /max-age\s*=\s*0(?:;|$)/i.test(value)) cookie = "";
      else if (/^[a-zA-Z0-9._-]+$/.test(match[1]))
        cookie = "JSESSIONID=" + match[1];
    }
  }
  async function send<T>(
    path: string,
    method: "GET" | "POST",
    data: unknown,
    form: boolean,
    generation: number,
  ): Promise<T> {
    if (generation !== epoch)
      throw new ApiError(0, "STALE_SESSION", "登录状态已改变，请重新加载。");
    const header: Record<string, string> = { Accept: "application/json" };
    if (cookie) header.Cookie = cookie;
    if (method === "POST") {
      header["content-type"] = form
        ? "application/x-www-form-urlencoded"
        : "application/json";
      if (csrf) header[csrf.headerName] = csrf.token;
    }
    let response: Response;
    try {
      response = await transport({
        url: API_ORIGIN + path,
        method,
        data,
        header,
        timeout: 15000,
      });
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError(0, "NETWORK", "无法连接服务，请检查本地后端后重试。");
    }
    if (generation !== epoch)
      throw new ApiError(0, "STALE_SESSION", "登录状态已改变，请重新加载。");
    absorb(response);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      const body = response.data as { code?: string; message?: string } | null;
      if (response.statusCode === 401) {
        clear();
        client.onUnauthorized?.();
      }
      if (response.statusCode === 403) csrf = null;
      throw new ApiError(
        response.statusCode,
        body?.code || "HTTP_ERROR",
        body?.message || "服务暂时不可用，请稍后重试。",
      );
    }
    return response.data as T;
  }
  const client = {
    onUnauthorized: undefined as (() => void) | undefined,
    clear,
    request<T>(
      path: string,
      method: "GET" | "POST" = "GET",
      data?: unknown,
      form = false,
    ): Promise<T> {
      const pathname = path.split("?", 1)[0];
      if (
        !/^\/[a-zA-Z0-9/_-]+$/.test(pathname) ||
        pathname.startsWith("//") ||
        /[\s#\\]/.test(path)
      ) {
        return Promise.reject(
          new ApiError(0, "INVALID_PATH", "请求地址无效。"),
        );
      }
      const generation = epoch;
      const result = queue.then(async () => {
        if (method === "POST" && !csrf)
          csrf = await send<Csrf>(
            "/auth/csrf",
            "GET",
            undefined,
            false,
            generation,
          );
        const value = await send<T>(path, method, data, form, generation);
        if (path === "/auth/demo-login" || path === "/auth/logout") csrf = null;
        return value;
      });
      queue = result.catch(() => undefined);
      return result;
    },
  };
  return client;
}
export function errorMessage(error: unknown): string {
  return error instanceof ApiError
    ? error.message
    : "暂时无法完成操作，请重试。";
}
