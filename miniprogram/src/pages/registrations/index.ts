import { api, registrationActions } from "../../services/api";
import { session } from "../../services/session";
import { Activity, Registration } from "../../services/types";
import { ApiError, errorMessage } from "../../services/client";
import { present, registrationLabel, beijing } from "../../domain/activity";
import { failure, login, confirmCancel, toast } from "../../ui/navigation";
interface Row {
  id: number;
  registration: Registration;
  activity: ReturnType<typeof present> | null;
  label: string;
  unavailable: boolean;
  title: string;
  updated: string;
  uncertain: boolean;
}
Page({
  data: {
    state: "loading",
    error: "",
    rows: [] as Row[],
    page: 0,
    total: 0,
    more: false,
    loadingMore: false,
    busyId: 0,
    feedback: "",
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
  onReachBottom() {
    void this.more();
  },
  async hydrate(records: Registration[]): Promise<Row[]> {
    return Promise.all(
      records.map(async (registration) => {
        let activity: Activity | null = null;
        let unavailable = false;
        try {
          activity = await api.detail(registration.activityId);
        } catch (error) {
          if (error instanceof ApiError && error.status === 404)
            unavailable = true;
          else throw error;
        }
        return {
          id: registration.activityId,
          registration,
          activity: activity ? present(activity) : null,
          label: registrationLabel(registration),
          unavailable,
          title:
            activity?.title || `活动 #${registration.activityId} · 已不可浏览`,
          updated: beijing(registration.updatedAt),
          uncertain: registrationActions.isUncertain(registration.activityId),
        };
      }),
    );
  },
  async load() {
    const seq = ++this.sequence;
    this.setData({
      state: "loading",
      rows: [],
      error: "",
      page: 0,
      total: 0,
      feedback: "",
      more: false,
      loadingMore: false,
    });
    try {
      await session.me();
      const page = await api.registrations();
      const rows = await this.hydrate(page.items);
      if (seq !== this.sequence || !session.user) return;
      this.setData({
        rows,
        page: 1,
        total: page.total,
        more: rows.length < page.total,
        state: rows.length ? "ready" : "empty",
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
      const page = await api.registrations(this.data.page + 1);
      const loaded = await this.hydrate(page.items);
      if (seq !== this.sequence || !session.user) return;
      const seen = new Set(this.data.rows.map((x) => x.registration.id));
      const rows = [
        ...this.data.rows,
        ...loaded.filter((x) => !seen.has(x.registration.id)),
      ];
      this.setData({
        rows,
        page: page.page,
        total: page.total,
        more: page.items.length === page.size && rows.length < page.total,
      });
    } catch (error) {
      if (seq === this.sequence) this.setData({ error: errorMessage(error) });
    } finally {
      if (seq === this.sequence) this.setData({ loadingMore: false });
    }
  },
  open(event: WechatMiniprogram.TouchEvent) {
    wx.navigateTo({
      url: "/pages/detail/index?id=" + event.currentTarget.dataset.id,
    });
  },
  async cancel(event: WechatMiniprogram.TouchEvent) {
    if (this.data.busyId) return;
    const id = Number(event.currentTarget.dataset.id);
    const row = this.data.rows.find((r) => r.registration.activityId === id);
    if (!row) return;
    const generation = session.generation;
    this.setData({ busyId: id, error: "", feedback: "" });
    try {
      if (!row.uncertain && !(await confirmCancel(row.title))) return;
      if (generation !== session.generation || !session.user) return;
      const result = row.uncertain
        ? await registrationActions.verify(id)
        : await registrationActions.change(id, row.registration.version, true);
      if (generation !== session.generation || !session.user) return;
      if (!result.verified) {
        this.setData({
          rows: this.data.rows.map((r) =>
            r.registration.activityId === id ? { ...r, uncertain: true } : r,
          ),
          error: "操作结果尚未确认，请点击“核实状态”后再操作。",
        });
        return;
      }
      const feedback = row.uncertain
        ? "已核实报名状态。"
        : result.registration?.status === "CANCELLED"
          ? "已取消报名，记录已保留。"
          : errorMessage(result.error);
      await this.load();
      if (generation !== session.generation || !session.user) return;
      this.setData({ feedback });
      toast(feedback);
    } finally {
      if (generation === session.generation) this.setData({ busyId: 0 });
    }
  },
  login,
  explore() {
    wx.switchTab({ url: "/pages/activities/index" });
  },
});
