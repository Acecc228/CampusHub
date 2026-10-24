import type { ActivityInput } from "../api/types";
export type ActivityForm = Omit<ActivityInput, "version">;
export const statusLabel = {
  DRAFT: "草稿",
  PUBLISHED: "已发布",
  UNPUBLISHED: "已下架",
};
export const roleLabel: Record<string, string> = {
  ADMIN: "管理员",
  ORGANIZER: "组织者",
  STUDENT: "学生",
};
export const canManage = (roles: string[]) =>
  roles.some((role) => role === "ADMIN" || role === "ORGANIZER");
export function safeReturnPath(value: unknown): string {
  return typeof value === "string" &&
    (value === "/" || /^\/activities(?:[/?]|$)/.test(value)) &&
    !value.includes("\\")
    ? value
    : "/";
}
export function toLocalTime(value: string) {
  if (!value) return "";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return "";
  return new Date(d.getTime() + 8 * 3600000)
    .toISOString()
    .slice(0, 19)
    .replace("T", " ");
}
export function toInstant(value: string) {
  if (!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(value)) return "";
  const d = new Date(value.replace(" ", "T") + "+08:00");
  if (
    Number.isNaN(d.getTime()) ||
    toLocalTime(d.toISOString()) !== value ||
    Number(value.slice(0, 4)) < 1000
  )
    return "";
  return d.toISOString().replace(".000Z", "Z");
}
export const formatDate = (value: string) =>
  toLocalTime(value).slice(0, 16) || "—";
export function validateActivity(form: ActivityForm, registered = 0) {
  const errors: Partial<Record<keyof ActivityForm, string>> = {};
  for (const [key, max] of Object.entries({
    title: 120,
    summary: 500,
    content: 20000,
    location: 200,
  })) {
    const name = key as "title" | "summary" | "content" | "location";
    if (!form[name].trim()) errors[name] = "请填写此项。";
    else if ([...form[name].trim()].length > max)
      errors[name] = `最多 ${max} 个字符。`;
  }
  if (!Number.isSafeInteger(form.categoryId) || form.categoryId < 1)
    errors.categoryId = "请选择活动分类。";
  if (
    !Number.isInteger(form.capacity) ||
    form.capacity < Math.max(1, registered) ||
    form.capacity > 2147483647
  )
    errors.capacity = `容量须为 ${Math.max(1, registered)}～2147483647 的整数。`;
  const dates = [
    "registrationStart",
    "registrationEnd",
    "startTime",
    "endTime",
  ] as const;
  for (const key of dates)
    if (!toInstant(form[key]))
      errors[key] = "请输入有效日期，格式为 YYYY-MM-DD HH:mm:ss。";
  if (!dates.some((key) => errors[key])) {
    if (form.registrationStart >= form.registrationEnd)
      errors.registrationEnd = "报名截止须晚于报名开始。";
    if (form.registrationEnd > form.startTime)
      errors.startTime = "活动开始不得早于报名截止。";
    if (form.startTime >= form.endTime)
      errors.endTime = "活动结束须晚于活动开始。";
  }
  return errors;
}
export function emptyForm(): ActivityForm {
  return {
    title: "",
    summary: "",
    content: "",
    categoryId: 0,
    location: "",
    capacity: 30,
    startTime: "",
    endTime: "",
    registrationStart: "",
    registrationEnd: "",
  };
}
