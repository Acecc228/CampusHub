import { session } from "../../services/session";
import { errorMessage } from "../../services/client";
Page({
  data: { username: "", password: "", error: "", busy: false },
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
    if (!this.data.username.trim() || !this.data.password) {
      this.setData({ error: "请填写学生账号和密码。" });
      return;
    }
    this.setData({ busy: true, error: "" });
    try {
      await session.login(this.data.username.trim(), this.data.password);
      if (getCurrentPages().length > 1) wx.navigateBack();
      else wx.switchTab({ url: "/pages/home/index" });
    } catch (error) {
      this.setData({ error: errorMessage(error) });
    } finally {
      this.setData({ busy: false, password: "" });
    }
  },
});
