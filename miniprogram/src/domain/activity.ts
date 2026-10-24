import { Activity, Registration } from "../services/types";
export function beijing(value: string | null) {
  if (!value) return "—";
  const d = new Date(new Date(value).getTime() + 8 * 3600000);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}/${pad(d.getUTCMonth() + 1)}/${pad(d.getUTCDate())} ${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}`;
}
export function availability(a: Activity, now = Date.now()) {
  if (now >= Date.parse(a.endTime))
    return { label: "活动已结束", enabled: false };
  if (now >= Date.parse(a.registrationEnd) || now >= Date.parse(a.startTime))
    return { label: "报名已截止", enabled: false };
  if (now < Date.parse(a.registrationStart))
    return { label: "报名未开始", enabled: false };
  if (a.remainingCapacity <= 0) return { label: "名额已满", enabled: false };
  return { label: "开放报名", enabled: true };
}
export function present(a: Activity) {
  const date = beijing(a.startTime);
  return {
    ...a,
    month: date.slice(5, 7),
    day: date.slice(8, 10),
    timeText: date,
    endText: beijing(a.endTime),
    registrationStartText: beijing(a.registrationStart),
    registrationEndText: beijing(a.registrationEnd),
    availability: availability(a),
    confirmed: a.myRegistration?.status === "CONFIRMED",
    canCancel: Date.now() < Date.parse(a.startTime),
  };
}
export function registrationLabel(r: Registration) {
  return r.status === "CONFIRMED"
    ? "已报名"
    : r.status === "CANCELLED"
      ? "已取消"
      : "未报名";
}
