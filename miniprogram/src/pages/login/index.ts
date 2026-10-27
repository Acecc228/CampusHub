import { DEMO_LOGIN } from "../../services/config";
import { ApiError } from "../../services/client";
import { session } from "../../services/session";
import { errorMessage } from "../../services/client";
Page({
  data: { username: "", password: "", error: "", busy: false, demo: DEMO_LOGIN, uncertain: false },
  username(event: WechatMiniprogram.Input) {
    this.setData({ username: event.detail.value });
  },
  password(event: WechatMiniprogram.Input) {
    this.setData({ password: event.detail.value });
  },
  onHide() {
    this.setData({ password: "" });
  },
  async submit() {
    if (this.data.busy) return;
    if (this.data.uncertain) return;
    if (DEMO_LOGIN && (!this.data.username.trim() || !this.data.password)) {
      this.setData({ error: "请填写学生账号和密码。" });
      return;
    }
    this.setData({ busy: true, error: "" });
    try {
      if (DEMO_LOGIN) await session.login(this.data.username.trim(), this.data.password);
      else {
        const code = await new Promise<string>((resolve, reject) => wx.login({ success: result => result.code ? resolve(result.code) : reject(new Error("微信登录未返回凭证")), fail: () => reject(new Error("微信登录失败")) }));
        await session.loginWechat(code);
      }
      if (getCurrentPages().length > 1) wx.navigateBack();
      else wx.switchTab({ url: "/pages/home/index" });
    } catch (error) {
      if (!DEMO_LOGIN && error instanceof ApiError && (error.status === 0 || error.status >= 500)) {
        this.setData({ uncertain: true, error: "登录结果暂时无法确认，请先核实登录状态。" });
      } else this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ busy: false, password: "" });
    }
  },
  async verify() {
    if (this.data.busy) return;
    this.setData({ busy: true });
    try {
      await session.me();
      this.setData({ uncertain: false });
      if (getCurrentPages().length > 1) wx.navigateBack();
      else wx.switchTab({ url: "/pages/home/index" });
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        this.setData({ uncertain: false, error: "当前尚未登录，请重新发起微信登录。" });
      } else this.setData({ error: "暂无法确认登录结果，请检查网络后再次核实。" });
    } finally { this.setData({ busy: false }); }
  },
});
