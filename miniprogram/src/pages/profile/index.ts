import { session } from "../../services/session";
import { Identity } from "../../services/types";
import { errorMessage } from "../../services/client";
import { failure, login } from "../../ui/navigation";
Page({
  data: {
    state: "loading",
    error: "",
    user: null as Identity | null,
    busy: false,
  },
  sequence: 0,
  onShow() {
    void this.load();
  },
  onHide() {
    this.sequence++;
  },
  async load() {
    const seq = ++this.sequence;
    this.setData({ state: "loading", error: "", user: null });
    try {
      const user = await session.me();
      if (seq === this.sequence) this.setData({ user, state: "ready" });
    } catch (error) {
      if (seq === this.sequence) this.setData(failure(error));
    }
  },
  async logout() {
    if (this.data.busy) return;
    this.setData({ busy: true, error: "" });
    try {
      await session.logout();
      this.setData({ state: "login", user: null });
    } catch (error) {
      this.setData({
        error:
          "暂时无法确认服务端退出，请恢复连接后重试。" + errorMessage(error),
      });
    } finally {
      this.setData({ busy: false });
    }
  },
  login,
  mine() {
    wx.switchTab({ url: "/pages/registrations/index" });
  },
});
