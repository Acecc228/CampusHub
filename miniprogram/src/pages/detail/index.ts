import { api, registrationActions } from "../../services/api";
import { session } from "../../services/session";
import { present } from "../../domain/activity";
import { errorMessage } from "../../services/client";
import { failure, login, confirmCancel, toast } from "../../ui/navigation";
Page({
  data: {
    state: "loading",
    error: "",
    activity: null as ReturnType<typeof present> | null,
    busy: false,
    uncertain: false,
    feedback: "",
  },
  id: 0,
  sequence: 0,
  onLoad(query: Record<string, string>) {
    this.id = Number(query.id);
  },
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
    this.setData({
      state: "loading",
      activity: null,
      error: "",
      uncertain: registrationActions.isUncertain(this.id),
    });
    try {
      await session.me();
      const activity = await api.detail(this.id);
      if (seq !== this.sequence || !session.user) return;
      this.setData({ state: "ready", activity: present(activity) });
    } catch (error) {
      if (seq === this.sequence) this.setData(failure(error));
    }
  },
  async act() {
    const a = this.data.activity;
    if (!a || this.data.busy || this.data.uncertain) return;
    const cancel = a.confirmed;
    this.setData({ busy: true, error: "", feedback: "" });
    const generation = session.generation;
    try {
      if (cancel && !(await confirmCancel(a.title))) return;
      if (generation !== session.generation || !session.user) return;
      const result = await registrationActions.change(
        this.id,
        a.myRegistration?.version || 0,
        cancel,
      );
      if (generation !== session.generation || !session.user) return;
      if (!result.verified) {
        this.setData({
          uncertain: true,
          error: "操作结果尚未确认，请先核实报名状态，不要重复提交。",
        });
        return;
      }
      const achieved =
        result.registration?.status === (cancel ? "CANCELLED" : "CONFIRMED");
      const feedback = achieved
        ? cancel
          ? "已取消报名，名额已释放。"
          : "报名已确认，去“我的报名”查看。"
        : errorMessage(result.error);
      await this.load();
      if (generation !== session.generation || !session.user) return;
      this.setData({ feedback });
      if (achieved) toast(cancel ? "已取消报名" : "报名已确认");
    } finally {
      if (generation === session.generation) this.setData({ busy: false });
    }
  },
  async verify() {
    if (this.data.busy) return;
    this.setData({ busy: true });
    const generation = session.generation;
    try {
      const result = await registrationActions.verify(this.id);
      if (generation !== session.generation) return;
      if (result.verified) {
        await this.load();
        if (generation !== session.generation || !session.user) return;
        this.setData({
          uncertain: false,
          feedback: "已核实，请以当前报名状态为准。",
        });
      } else
        this.setData({
          error: "仍无法核实，请恢复网络后重试。",
          uncertain: true,
        });
    } finally {
      if (generation === session.generation) this.setData({ busy: false });
    }
  },
  login,
  mine() {
    wx.switchTab({ url: "/pages/registrations/index" });
  },
});
