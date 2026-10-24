import { describe, expect, it } from "vitest";
import {
  validateActivity,
  toInstant,
  toLocalTime,
  safeReturnPath,
  canManage,
} from "./activity";

const valid = {
  title: "校园开放日",
  summary: "一起探索校园",
  content: "活动安排",
  categoryId: 1,
  location: "图书馆",
  capacity: 30,
  registrationStart: "2026-11-01 09:00:00",
  registrationEnd: "2026-11-02 09:00:00",
  startTime: "2026-11-02 10:00:00",
  endTime: "2026-11-02 11:00:00",
};
describe("管理端领域校验", () => {
  it("合法字段通过，报名截止允许等于开始", () => {
    expect(validateActivity(valid)).toEqual({});
    expect(
      validateActivity({ ...valid, registrationEnd: valid.startTime }),
    ).toEqual({});
  });
  it("拒绝错误时间顺序和不存在日期", () => {
    expect(
      validateActivity({ ...valid, registrationEnd: valid.registrationStart })
        .registrationEnd,
    ).toBeTruthy();
    expect(
      validateActivity({ ...valid, startTime: "2026-02-30 10:00:00" })
        .startTime,
    ).toBeTruthy();
  });
  it("拒绝空白、超长文本、非整数容量和小于报名数的容量", () => {
    expect(
      validateActivity({ ...valid, title: "  ", capacity: 2.5 }).title,
    ).toBeTruthy();
    expect(validateActivity({ ...valid, capacity: 2.5 }).capacity).toBeTruthy();
    expect(
      validateActivity({ ...valid, summary: "字".repeat(501) }).summary,
    ).toBeTruthy();
    expect(validateActivity(valid, 31).capacity).toBeTruthy();
  });
  it("北京时间与 API UTC 整秒稳定互转", () => {
    expect(toInstant("2026-11-01 09:00:00")).toBe("2026-11-01T01:00:00Z");
    expect(toLocalTime("2026-11-01T01:00:00Z")).toBe("2026-11-01 09:00:00");
  });
  it("只接受内部管理路由作为返回地址", () => {
    expect(safeReturnPath("//evil.example")).toBe("/");
    expect(safeReturnPath("/login")).toBe("/");
    expect(safeReturnPath("/activities?page=2")).toBe("/activities?page=2");
  });
  it("学生不能访问管理端", () => {
    expect(canManage(["STUDENT"])).toBe(false);
    expect(canManage(["ORGANIZER"])).toBe(true);
    expect(canManage(["ADMIN"])).toBe(true);
  });
});
