import { ApiError, errorMessage } from "../services/client";
export function failure(error: unknown) {
  return {
    state:
      error instanceof ApiError && error.status === 401 ? "login" : "error",
    error: errorMessage(error),
  };
}
export function login() {
  wx.navigateTo({ url: "/pages/login/index" });
}
export function detail(event: WechatMiniprogram.CustomEvent<{ id: number }>) {
  wx.navigateTo({ url: "/pages/detail/index?id=" + event.detail.id });
}
export function confirmCancel(title: string): Promise<boolean> {
  return new Promise((resolve) =>
    wx.showModal({
      title: "取消报名",
      content: `确定取消“${title}”的报名吗？名额将释放，重新报名需符合当时的条件。`,
      confirmText: "取消报名",
      cancelText: "保留报名",
      confirmColor: "#b42318",
      success: (r) => resolve(r.confirm),
      fail: () => resolve(false),
    }),
  );
}
export function toast(title: string) {
  wx.showToast({ title, icon: "none" });
}
