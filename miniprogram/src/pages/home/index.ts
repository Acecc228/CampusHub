import { api } from "../../services/api";
import { session } from "../../services/session";
import { present } from "../../domain/activity";
import { Category } from "../../services/types";
import { failure, login, detail } from "../../ui/navigation";
Page({
  data: {
    state: "loading",
    error: "",
    items: [] as ReturnType<typeof present>[],
    categories: [] as Category[],
    total: 0,
  },
  sequence: 0,
  onShow() {
    void this.load();
  },
  onHide() {
    this.sequence++;
  },
  onUnload() {
    this.sequence++;
  },
  async onPullDownRefresh() {
    try {
      await this.load();
    } finally {
      wx.stopPullDownRefresh();
    }
  },
  async load() {
    const seq = ++this.sequence;
    this.setData({ state: "loading", items: [], error: "" });
    try {
      await session.me();
      const [page, categories] = await Promise.all([
        api.activities(1, "", 0, 4),
        api.categories(),
      ]);
      if (seq !== this.sequence || !session.user) return;
      this.setData({
        state: page.items.length ? "ready" : "empty",
        items: page.items.map(present),
        categories,
        total: page.total,
      });
    } catch (error) {
      if (seq === this.sequence) this.setData(failure(error));
    }
  },
  login,
  detail,
  explore() {
    wx.switchTab({ url: "/pages/activities/index" });
  },
  category(event: WechatMiniprogram.TouchEvent) {
    getApp().globalData = {
      categoryId: Number(event.currentTarget.dataset.id),
    };
    this.explore();
  },
});
