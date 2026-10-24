import { test, expect, type Page } from "@playwright/test";
import { readFileSync } from "node:fs";
if (process.env.CH_E2E_APPLY !== "true")
  throw new Error("Use Test-AdminWeb.ps1 -Apply");
const secrets = JSON.parse(
  readFileSync(
    "E:/CampusHubRuntime/mysql84/secrets/campushub_test-demo.json",
    "utf8",
  ),
) as { Database: string; Accounts: { LoginName: string; Password: string }[] };
if (secrets.Database !== "campushub_test")
  throw new Error("Test database required");
async function setup(page: Page) {
  const token = await (await page.request.get("/api/v1/auth/csrf")).json();
  const account = secrets.Accounts.find(
    (a) => a.LoginName === "demo_organizer",
  )!;
  expect(
    (
      await page.request.post("/api/v1/auth/demo-login", {
        headers: { [token.headerName]: token.token },
        form: { username: account.LoginName, password: account.Password },
      })
    ).status(),
  ).toBe(200);
  const csrf = await (await page.request.get("/api/v1/auth/csrf")).json();
  const categories = await (
    await page.request.get("/api/v1/activity-categories")
  ).json();
  const at = (hour: number) =>
    new Date(Date.now() + hour * 3600000)
      .toISOString()
      .replace(/\.\d{3}Z$/, "Z");
  const response = await page.request.post("/api/v1/manage/activities", {
    headers: { [csrf.headerName]: csrf.token },
    data: {
      title: "M14A 审查回归 " + Date.now(),
      summary: "真实测试",
      content: "真实活动内容",
      location: "图书馆",
      capacity: 30,
      categoryId: categories[0].id,
      registrationStart: at(-1),
      registrationEnd: at(48),
      startTime: at(72),
      endTime: at(73),
    },
  });
  expect(response.status()).toBe(201);
  return (await response.json()).id as number;
}
test("审查回归：保存等待期间阻止编辑丢失", async ({ page }) => {
  const id = await setup(page);
  await page.goto(`/activities/${id}/edit`);
  await page.locator("#summary").fill("待保存修改");
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(`**/api/v1/manage/activities/${id}`, async (route) => {
    if (route.request().method() === "PUT") await gate;
    await route.continue();
  });
  await page.getByRole("button", { name: "保存修改" }).click();
  try {
    await expect(page.locator("#summary")).toBeDisabled();
    await expect(
      page.getByRole("combobox", { name: "活动分类", exact: true }),
    ).toBeDisabled();
  } finally {
    release();
  }
  await expect(page).toHaveURL(/\/activities$/);
});
test("审查回归：Session 失效关闭包含业务信息的确认框", async ({ page }) => {
  const id = await setup(page);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route(
    `**/api/v1/manage/activities/${id}/registrations?*`,
    async (route) => {
      await gate;
      await route.continue();
    },
  );
  await page.goto(`/activities/${id}?tab=registrations`);
  await page.getByRole("button", { name: "发布活动", exact: true }).click();
  await expect(page.locator(".el-message-box")).toBeVisible();
  const csrf = await (await page.request.get("/api/v1/auth/csrf")).json();
  expect(
    (
      await page.request.post("/api/v1/auth/logout", {
        headers: { [csrf.headerName]: csrf.token },
      })
    ).status(),
  ).toBe(204);
  release();
  await expect(
    page.getByRole("heading", { name: "重新连接工作台" }),
  ).toBeVisible();
  await expect(page.locator(".el-message-box")).toHaveCount(0);
  await page.locator("#username").fill("demo_organizer");
  await page
    .locator("#password")
    .fill(
      secrets.Accounts.find((a) => a.LoginName === "demo_organizer")!.Password,
    );
  await page.getByRole("button", { name: "登录管理中心" }).click();
  await expect(
    page.getByRole("button", { name: "发布活动", exact: true }),
  ).toBeEnabled();
});

test("真实十一名学生报名后的名单翻页与边界", async ({ page, playwright }) => {
  const id = await setup(page);
  const token = await (await page.request.get("/api/v1/auth/csrf")).json();
  expect(
    (
      await page.request.post(`/api/v1/manage/activities/${id}/publish`, {
        headers: { [token.headerName]: token.token },
        data: { version: 0 },
      })
    ).status(),
  ).toBe(200);
  const students = JSON.parse(process.env.CH_E2E_STUDENTS || "[]") as string[];
  expect(students).toHaveLength(11);
  for (const username of students) {
    const student = await playwright.request.newContext({
      baseURL: "http://127.0.0.1:5173",
    });
    const csrf = await (await student.get("/api/v1/auth/csrf")).json();
    expect(
      (
        await student.post("/api/v1/auth/demo-login", {
          headers: { [csrf.headerName]: csrf.token },
          form: {
            username,
            password: secrets.Accounts.find(
              (a) => a.LoginName === "demo_student",
            )!.Password,
          },
        })
      ).status(),
    ).toBe(200);
    const authenticated = await (await student.get("/api/v1/auth/csrf")).json();
    expect(
      (
        await student.post(`/api/v1/activities/${id}/registrations`, {
          headers: { [authenticated.headerName]: authenticated.token },
          data: { version: 0 },
        })
      ).status(),
    ).toBe(200);
    await student.dispose();
  }
  await page.goto(`/activities/${id}?tab=registrations`);
  await expect(
    page.getByText("有效报名 11 人，剩余 19 个名额。", { exact: false }),
  ).toBeVisible();
  await expect(page.locator("tbody tr")).toHaveCount(10);
  await page.getByRole("button", { name: "下一页", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await expect(
    page.getByRole("button", { name: "下一页", exact: true }),
  ).toBeDisabled();
  await page.reload();
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page.getByRole("button", { name: "上一页", exact: true }).click();
  await expect(page.locator("tbody tr")).toHaveCount(10);
});
