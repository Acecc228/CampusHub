// Actual simulator only. No mock data or browser-rendered replicas.
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const automator = require("miniprogram-automator");
if (process.env.CH_MINI_APPLY !== "true")
  throw Error("Use simulator wrapper -Apply -VisualLabel");
const label = process.argv[2];
assert.match(label, /^[a-z0-9-]{1,24}$/);
const root = path.resolve(__dirname, "../..");
const secrets = JSON.parse(
  fs
    .readFileSync(
      "E:/CampusHubRuntime/mysql84/secrets/campushub_test-demo.json",
      "utf8",
    )
    .replace(/^\uFEFF/, ""),
);
assert.equal(secrets.Database, "campushub_test");
const account = secrets.Accounts.find((x) => x.LoginName === "demo_student");
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const evidence = { label, pages: [] };
let mini;
async function ready(page) {
  await wait(800);
  for (let i = 0; i < 100; i++) {
    if ((await page.data("state")) !== "loading") return;
    await wait(150);
  }
  throw Error("Page loading timeout");
}
async function capture(page, name) {
  await ready(page);
  const data = await page.data();
  if (name !== "login") assert.equal(data.state, "ready");
  await mini.pageScrollTo(0);
  await wait(350);
  await mini.screenshot({
    path: path.join(
      root,
      "docs/verification/m1.4-b-screenshots",
      `${label}-${name}.png`,
    ),
  });
  evidence.pages.push({ name, state: data.state || "form", status: "PASS" });
}
(async () => {
  mini = await automator.connect({ wsEndpoint: "ws://127.0.0.1:9420" });
  const info = await mini.systemInfo();
  evidence.system = {
    width: info.windowWidth,
    height: info.windowHeight,
    platform: info.platform,
    SDKVersion: info.SDKVersion,
  };
  let page = await mini.reLaunch("/pages/login/index");
  await capture(page, "login");
  await (await page.$("#username")).input(account.LoginName);
  await (await page.$("#password")).input(account.Password);
  await (await page.$(".primary")).tap();
  for (let i = 0; i < 100; i++) {
    page = await mini.currentPage();
    if (page.path === "pages/home/index") break;
    await wait(150);
  }
  await capture(page, "home");
  const id = (await page.data("items"))[0].id;
  page = await mini.switchTab("/pages/activities/index");
  await capture(page, "activities");
  if (await page.data("more")) {
    evidence.stage = "pagination";
    const before = (await page.data("items")).length;
    // Scroll the real viewport to trigger onReachBottom; offscreen tap is not reliable.
    await mini.pageScrollTo(100000);
    await wait(500);
    for (let i = 0; i < 100; i++) {
      if (
        !(await page.data("loadingMore")) &&
        (await page.data("items")).length > before
      )
        break;
      await wait(150);
    }
    const items = await page.data("items");
    assert.ok(items.length > before);
    assert.equal(new Set(items.map((x) => x.id)).size, items.length);
    evidence.pagination = "PASS";
  }
  await mini.callWxMethod("startPullDownRefresh");
  await ready(page);
  assert.equal(await page.data("state"), "ready");
  evidence.refresh = "PASS";
  page = await mini.navigateTo("/pages/detail/index?id=" + id);
  await capture(page, "detail");
  await mini.pageScrollTo(1200);
  await wait(350);
  await mini.screenshot({
    path: path.join(
      root,
      "docs/verification/m1.4-b-screenshots",
      `${label}-detail-bottom.png`,
    ),
  });
  page = await mini.navigateBack();
  assert.equal(page.path, "pages/activities/index");
  evidence.back = "PASS";
  page = await mini.switchTab("/pages/registrations/index");
  await capture(page, "registrations");
  page = await mini.switchTab("/pages/profile/index");
  await capture(page, "profile");
})()
  .catch((error) => {
    evidence.status = "FAIL";
    evidence.reason = String(error.message)
      .replace(account.Password, "[REDACTED]")
      .slice(0, 180);
    process.exitCode = 1;
  })
  .finally(async () => {
    evidence.status = evidence.status || "PASS";
    fs.writeFileSync(
      path.join(root, `docs/verification/m1.4-b-visual-${label}.json`),
      JSON.stringify(evidence, null, 2),
    );
    console.log(JSON.stringify(evidence));
    if (mini) await mini.disconnect();
    process.exit(process.exitCode || 0);
  });
