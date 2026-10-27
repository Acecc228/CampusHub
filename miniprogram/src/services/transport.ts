import { DEMO_LOGIN } from "./config";
import { ApiError, createClient, Transport } from "./client";
export const wxTransport: Transport = (input) =>
  new Promise((resolve, reject) => {
    if (DEMO_LOGIN && wx.getDeviceInfo().platform !== "devtools") {
      reject(
        new ApiError(
          0,
          "SIMULATOR_ONLY",
          "此版本仅支持微信开发者工具本地模拟器。",
        ),
      );
      return;
    }
    wx.request({
      ...input,
      data: input.data as string | object | undefined,
      success: (result) => resolve(result),
      fail: (result) =>
        reject(
          new ApiError(
            0,
            /timeout/i.test(result.errMsg) ? "TIMEOUT" : "NETWORK",
            /timeout/i.test(result.errMsg)
              ? "请求超时，写入结果需核实。"
              : "无法连接服务，请检查网络设置。",
          ),
        ),
    });
  });
export const http = createClient(wxTransport);
