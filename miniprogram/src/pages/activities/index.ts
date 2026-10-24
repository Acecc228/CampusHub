import { api } from "../../services/api";
import { session } from "../../services/session";
import { present } from "../../domain/activity";
import { Category } from "../../services/types";
import { failure, login, detail } from "../../ui/navigation";
Page({
  data: {
    state: "loading",
    error: "",
    keyword: "",
    categoryId: 0,
    categories: [] as Category[],
    items: [] as ReturnType<typeof present>[],
    total: 0,
    page: 0,
    more: false,
    loadingMore: false,
  },
  sequence: 0,
  committedKeyword: "",
  onShow() {
    const app = getApp();
    if (app.globalData?.categoryId) {
      this.setData({ categoryId: app.globalData.categoryId });
      app.globalData.categoryId = 0;
    }
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
  onReachBottom() {
    void this.more();
  },
  input(event: WechatMiniprogram.Input) {
    this.setData({ keyword: event.detail.value });
  },
  search() {
    void this.load();
  },
  clear() {
    this.setData({ keyword: "", categoryId: 0 });
    void this.load();
  },
  filter(event: WechatMiniprogram.TouchEvent) {
    this.setData({ categoryId: Number(event.currentTarget.dataset.id) });
    void this.load();
  },
  async load() {
    this.committedKeyword = this.data.keyword.trim();
    const seq = ++this.sequence;
    this.setData({
      state: "loading",
      items: [],
      page: 0,
      total: 0,
      more: false,
      loadingMore: false,
      error: "",
    });
    try {
      await session.me();
      const [page, categories] = await Promise.all([
        api.activities(1, this.committedKeyword, this.data.categoryId),
        api.categories(),
      ]);
      if (seq !== this.sequence || !session.user) return;
      this.setData({
        items: page.items.map(present),
        categories,
        total: page.total,
        page: 1,
        more: page.total > page.items.length,
        state: page.total ? "ready" : "empty",
      });
    } catch (error) {
      if (seq === this.sequence) this.setData(failure(error));
    }
  },
  async more() {
    if (this.data.loadingMore || !this.data.more || this.data.state !== "ready")
      return;
    const seq = this.sequence;
    this.setData({ loadingMore: true, error: "" });
    try {
      const page = await api.activities(
        this.data.page + 1,
        this.committedKeyword,
        this.data.categoryId,
      );
      if (seq !== this.sequence || !session.user) return;
      const existing = new Set(this.data.items.map((x) => x.id));
      const items = [
        ...this.data.items,
        ...page.items.filter((x) => !existing.has(x.id)).map(present),
      ];
      this.setData({
        items,
        page: page.page,
        total: page.total,
        more: page.items.length === page.size && items.length < page.total,
      });
    } catch (error) {
      if (seq === this.sequence) this.setData({ error: failure(error).error });
    } finally {
      if (seq === this.sequence) this.setData({ loadingMore: false });
    }
  },
  login,
  detail,
});
