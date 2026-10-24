import {
  test,
  expect,
  type Page,
  type APIRequestContext,
} from "@playwright/test";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { toLocalTime } from "../src/domain/activity";
import type { Activity, Category } from "../src/api/types";

if (process.env.CH_E2E_APPLY !== "true")
  throw new Error(
    "Real tests require scripts/Test-AdminWeb.ps1 -Apply and the dedicated test backend.",
  );
const secrets = JSON.parse(
  readFileSync(
    "E:/CampusHubRuntime/mysql84/secrets/campushub_test-demo.json",
    "utf8",
  ),
) as { Database: string; Accounts: { LoginName: string; Password: string }[] };
if (secrets.Database !== "campushub_test" || secrets.Accounts.length !== 3)
  throw new Error("Invalid test identity inventory");
const run = "M14A-" + Date.now();
const evidence = "../docs/verification/m1.4-a-screenshots";
mkdirSync(evidence, { recursive: true });
let createdId = 0;
const errors: string[] = [];

function password(role: string) {
  const account = secrets.Accounts.find((a) => a.LoginName === "demo_" + role);
  if (!account) throw new Error("Missing test identity");
  return account.Password;
}
async function login(page: Page, role = "organizer") {
  await page.goto("/login");
  await page.locator("#username").fill("demo_" + role);
  await page.locator("#password").fill(password(role));
  await page.getByRole("button", { name: "登录管理中心", exact: true }).click();
  await expect(page).not.toHaveURL(/login/);
}
async function csrf(request: APIRequestContext) {
  const response = await request.get("/api/v1/auth/csrf");
  expect(response.status()).toBe(200);
  return response.json() as Promise<{ headerName: string; token: string }>;
}
async function apiLogin(request: APIRequestContext, role: string) {
  const token = await csrf(request);
  const response = await request.post("/api/v1/auth/demo-login", {
    headers: { [token.headerName]: token.token },
    form: { username: "demo_" + role, password: password(role) },
  });
  expect(response.status()).toBe(200);
}
async function mutate(
  request: APIRequestContext,
  path: string,
  data: unknown,
  method: "POST" | "PUT" = "POST",
) {
  const token = await csrf(request);
  return request.fetch("/api/v1" + path, {
    method,
    headers: { [token.headerName]: token.token },
    data,
  });
}
function activityInput(title: string, categoryId: number) {
  const now = Date.now(),
    at = (hours: number) =>
      new Date(now + hours * 3600000).toISOString().replace(/\.\d{3}Z$/, "Z");
  return {
    title,
    summary: "真实 API 联调活动：在校园里分享想法。",
    content:
      "欢迎参加 CampusHub 校园共创活动。\n交流、实践、分享，连接志同道合的同学。",
    categoryId,
    location: "图书馆 · 共享空间",
    capacity: 30,
    registrationStart: at(-2),
    registrationEnd: at(48),
    startTime: at(72),
    endTime: at(74),
  };
}
async function fillActivity(page: Page, title: string) {
  const categories = (await (
    await page.request.get("/api/v1/activity-categories")
  ).json()) as Category[];
  const input = activityInput(title, categories[0]!.id);
  await page.locator("#title").fill(input.title);
  await page.locator("#summary").fill(input.summary);
  await page.screenshot({
    path: evidence + "/form-before-select.png",
    fullPage: true,
  });
  await page
    .getByRole("combobox", { name: "活动分类", exact: true })
    .press("ArrowDown");
  await page
    .getByRole("option", { name: categories[0]!.name, exact: true })
    .click();
  await page.locator("#location").fill(input.location);
  await page.locator("#content").fill(input.content);
  for (const key of [
    "registrationStart",
    "registrationEnd",
    "startTime",
    "endTime",
  ] as const)
    await page.locator("#" + key).fill(toLocalTime(input[key]));
}
test.describe.configure({ mode: "serial" });
test.beforeEach(async ({ page }) => {
  page.on("pageerror", (error) => errors.push(error.message));
});
test.afterEach(() => {
  writeFileSync(
    "../docs/verification/m1.4-a-browser-errors.json",
    JSON.stringify(errors, null, 2),
  );
});

test("组织者真实创建、编辑、发布、学生报名、下架和重发布闭环", async ({
  page,
  playwright,
}) => {
  await login(page);
  await page.getByRole("link", { name: "创建活动", exact: true }).click();
  await page.getByRole("button", { name: "创建草稿" }).click();
  await expect(page.locator("#title")).toHaveAttribute("aria-invalid", "true");
  await expect(page.locator("#title")).toBeFocused();
  await fillActivity(page, run + " 校园共创工作坊");
  await page.screenshot({ path: evidence + "/form-1440.png", fullPage: true });
  let creates = 0;
  page.on("request", (request) => {
    if (
      request.method() === "POST" &&
      request.url().endsWith("/manage/activities")
    )
      creates++;
  });
  await page.getByRole("button", { name: "创建草稿" }).dblclick();
  await expect(page).toHaveURL(/\/activities$/);
  expect(creates).toBe(1);
  await page
    .getByRole("link", { name: run + " 校园共创工作坊", exact: true })
    .click();
  await expect(page).toHaveURL(/\/activities\/\d+/);
  createdId = Number(new URL(page.url()).pathname.split("/").pop());
  expect(createdId).toBeGreaterThan(0);
  await page.getByRole("link", { name: "编辑活动", exact: true }).click();
  await page.locator("#title").fill(run + " 秋日校园共创工作坊");
  await page.getByRole("button", { name: "保存修改" }).click();
  await page
    .getByRole("link", { name: run + " 秋日校园共创工作坊", exact: true })
    .click();
  await page.getByRole("button", { name: "发布活动", exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.screenshot({ path: evidence + "/publish-dialog.png" });
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "发布活动", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "下架活动", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("link", { name: "编辑活动", exact: true }),
  ).toHaveCount(0);

  const student = await playwright.request.newContext({
    baseURL: "http://127.0.0.1:5173",
  });
  await apiLogin(student, "student");
  expect((await student.get(`/api/v1/activities/${createdId}`)).status()).toBe(
    200,
  );
  const registration = await mutate(
    student,
    `/activities/${createdId}/registrations`,
    { version: 0 },
  );
  expect(registration.status()).toBe(200);
  const registered = (await registration.json()) as { version: number };
  await page.getByRole("button", { name: "刷新", exact: true }).click();
  await page.getByRole("link", { name: "报名名单 · 1", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(page.locator("tbody")).toContainText("已报名");
  await page.screenshot({
    path: evidence + "/registrations-1440.png",
    fullPage: true,
  });
  await page.getByRole("button", { name: "下架活动", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "下架活动", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "重新发布", exact: true }),
  ).toBeVisible();
  expect((await student.get(`/api/v1/activities/${createdId}`)).status()).toBe(
    404,
  );
  expect(
    (
      await mutate(student, `/activities/${createdId}/registrations/cancel`, {
        version: registered.version,
      })
    ).status(),
  ).toBe(200);
  await page.getByRole("button", { name: "刷新名单", exact: true }).click();
  await expect(page.locator("tbody")).toContainText("已取消");
  await expect(
    page.getByText("有效报名 0 人，剩余 30 个名额。", { exact: false }),
  ).toBeVisible();
  await page.getByRole("button", { name: "重新发布", exact: true }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "重新发布", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "下架活动", exact: true }),
  ).toBeVisible();
  expect((await student.get(`/api/v1/activities/${createdId}`)).status()).toBe(
    200,
  );
  await student.dispose();
  writeFileSync(
    "../docs/verification/m1.4-a-fixture.json",
    JSON.stringify(
      { run, createdId, retained: true, database: "campushub_test" },
      null,
      2,
    ),
  );
});

test("管理员真实筛选、分页、跨组织者管理和多尺寸视觉", async ({ page }) => {
  await login(page, "admin");
  const categories = (await (
    await page.request.get("/api/v1/activity-categories")
  ).json()) as Category[];
  for (let i = 0; i < 12; i++)
    expect(
      (
        await mutate(
          page.request,
          "/manage/activities",
          activityInput(
            `${run} 管理员活动 ${i + 1}`,
            categories[i % categories.length]!.id,
          ),
        )
      ).status(),
    ).toBe(201);
  await page.goto("/activities?keyword=" + run);
  await expect(page.locator("tbody tr")).toHaveCount(10);
  await page.getByRole("button", { name: "下一页", exact: true }).click();
  await expect(page).toHaveURL(/page=2/);
  await expect(page.locator("tbody tr")).toHaveCount(3);
  await page.reload();
  await expect(page.locator("tbody tr")).toHaveCount(3);
  await page.getByRole("combobox", { name: "活动状态筛选" }).press("ArrowDown");
  await page.getByRole("option", { name: "已发布", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("combobox", { name: "活动分类筛选" }).press("ArrowDown");
  await page.screenshot({ path: evidence + "/filter-popup.png" });
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "清除筛选", exact: true }).click();
  for (const width of [1440, 1280, 900]) {
    await page.setViewportSize({ width, height: 960 });
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "最近创建的活动" }),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: `${evidence}/dashboard-${width}.png`,
      fullPage: true,
    });
    await page.goto("/activities");
    await expect(page.locator("tbody tr").first()).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
    await page.screenshot({
      path: `${evidence}/activities-${width}.png`,
      fullPage: true,
    });
  }
  await page.goto(`/activities/${createdId}`);
  await expect(
    page.getByRole("heading", { name: run + " 秋日校园共创工作坊" }),
  ).toBeVisible();
  await page.screenshot({ path: evidence + "/detail-900.png", fullPage: true });
});

test("真实认证失败、CSRF 拒绝、学生 403、组织者归属隔离", async ({
  page,
  playwright,
}) => {
  await page.goto("/login");
  await page.locator("#username").fill("demo_admin");
  await page.locator("#password").fill("not-the-password");
  await page.getByRole("button", { name: "登录管理中心" }).click();
  await expect(page.getByRole("alert")).toContainText("账号或密码错误");
  await login(page, "student");
  await expect(page).toHaveURL(/403/);
  expect((await page.request.get("/api/v1/manage/activities")).status()).toBe(
    403,
  );
  await page.getByRole("button", { name: "退出登录" }).click();
  await expect(page).toHaveURL(/login/);
  expect((await page.request.get("/api/v1/auth/me")).status()).toBe(401);
  await login(page, "organizer");
  expect(
    (
      await page.request.post(
        `/api/v1/manage/activities/${createdId}/unpublish`,
        { data: { version: 1 } },
      )
    ).status(),
  ).toBe(403);
  const admin = await playwright.request.newContext({
    baseURL: "http://127.0.0.1:5173",
  });
  await apiLogin(admin, "admin");
  const adminList = (await (
    await admin.get(
      "/api/v1/manage/activities?keyword=" +
        encodeURIComponent(run + " 管理员活动"),
    )
  ).json()) as { items: Activity[] };
  const other = adminList.items[0]!.id;
  expect(
    (await page.request.get(`/api/v1/manage/activities/${other}`)).status(),
  ).toBe(403);
  expect(
    (
      await page.request.get(`/api/v1/manage/activities/${other}/registrations`)
    ).status(),
  ).toBe(403);
  await page.goto(`/activities/${other}`);
  await expect(page.getByRole("alert")).toContainText("无权管理此活动");
  await admin.dispose();
});

test("真实版本冲突保留输入，未保存保护和 Session 失效后同身份恢复", async ({
  page,
}) => {
  await login(page, "organizer");
  const current = (await (
    await page.request.get(`/api/v1/manage/activities/${createdId}`)
  ).json()) as Activity;
  expect(
    (
      await mutate(page.request, `/manage/activities/${createdId}/unpublish`, {
        version: current.version,
      })
    ).status(),
  ).toBe(200);
  await page.goto(`/activities/${createdId}/edit`);
  await expect(page.locator("#title")).toHaveValue(run + " 秋日校园共创工作坊");
  const latest = (await (
    await page.request.get(`/api/v1/manage/activities/${createdId}`)
  ).json()) as Activity;
  expect(
    (
      await mutate(
        page.request,
        `/manage/activities/${createdId}`,
        { ...latest, summary: "另一会话更新的信息" },
        "PUT",
      )
    ).status(),
  ).toBe(200);
  await page.locator("#summary").fill("当前未保存的想法");
  await page.getByRole("button", { name: "保存修改" }).click();
  await expect(page.getByRole("alert").first()).toContainText("版本已变更");
  await expect(page.locator("#summary")).toHaveValue("当前未保存的想法");
  await page.getByRole("link", { name: "返回活动列表 →" }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "取消", exact: true })
    .click();
  const token = await csrf(page.request);
  expect(
    (
      await page.request.post("/api/v1/auth/logout", {
        headers: { [token.headerName]: token.token },
      })
    ).status(),
  ).toBe(204);
  await page.getByRole("button", { name: "保存修改" }).click();
  await expect(
    page.getByRole("heading", { name: "重新连接工作台" }),
  ).toBeVisible();
  await page.locator("#username").fill("demo_organizer");
  await page.locator("#password").fill(password("organizer"));
  await page.getByRole("button", { name: "登录管理中心" }).click();
  await expect(page.locator("#summary")).toBeVisible();
  await expect(page.locator("#summary")).toHaveValue("当前未保存的想法");
  await page.getByRole("button", { name: "保存修改" }).click();
  await expect(page.getByRole("alert").first()).toContainText("版本已变更");
  await page.getByRole("button", { name: "重新读取活动" }).click();
  await page
    .getByRole("dialog")
    .getByRole("button", { name: "重新读取", exact: true })
    .click();
  await expect(page.locator("#summary")).toHaveValue("另一会话更新的信息");
});

test("网络失败、无结果、慢请求、搜索乱序保护与 404", async ({
  page,
  context,
}) => {
  await login(page, "organizer");
  await page.goto("/activities?keyword=definitely-no-such-campus-activity");
  await expect(
    page.getByRole("heading", { name: "没有找到匹配活动" }),
  ).toBeVisible();
  await page.screenshot({ path: evidence + "/empty-state.png" });
  await context.setOffline(true);
  await page.getByRole("button", { name: "刷新", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("无法连接服务");
  await page.screenshot({ path: evidence + "/network-error.png" });
  await context.setOffline(false);
  await page.getByRole("button", { name: "重新加载", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "没有找到匹配活动" }),
  ).toBeVisible();
  // Fault injection delays actual network requests; no successful responses are mocked.
  await page.route("**/api/v1/manage/activities?*", async (route) => {
    if (route.request().url().includes("keyword=slow"))
      await new Promise((resolve) => setTimeout(resolve, 1200));
    await route.continue().catch(() => {});
  });
  const search = page.getByLabel("搜索活动", { exact: true });
  await search.fill("slow");
  await page.waitForTimeout(350);
  await expect(page.getByText("正在加载…", { exact: true })).toBeVisible();
  await search.fill(run);
  await search.press("Enter");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.waitForTimeout(1400);
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("button", { name: "清除搜索", exact: true }).click();
  await expect(search).toHaveValue("");
  await expect(search).toBeFocused();
  await page.goto("/not-a-real-page");
  await expect(page.getByRole("heading", { name: "页面不存在" })).toBeVisible();
  expect(errors).toEqual([]);
});
