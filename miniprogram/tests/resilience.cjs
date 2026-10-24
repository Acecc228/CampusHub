const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const automator = require("miniprogram-automator");
if (process.env.CH_MINI_APPLY !== "true")
  throw Error("Explicit CH_MINI_APPLY=true required");
const mode = process.argv[2];
assert.ok(["offline", "recovery", "punctuation"].includes(mode));
const root = path.resolve(__dirname, "../..");
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
let mini;
const evidence = {
  mode,
  status: "FAIL",
  transport: "actual wx.request, no mocked responses",
};
const timer = setTimeout(() => {
  console.log("Simulator check timed out");
  process.exit(2);
}, 45000);
async function ready(page) {
  await wait(600);
  for (let i = 0; i < 150; i++) {
    if ((await page.data("state")) !== "loading") return;
    await wait(150);
  }
  throw Error("Page timeout");
}
(async () => {
  mini = await automator.connect({ wsEndpoint: "ws://127.0.0.1:9420" });
  let page;
  if (mode === "punctuation") {
    page = await mini.switchTab("/pages/activities/index");
    await ready(page);
    for (const keyword of ["O'Reilly", "活动 (线上)", "你好!", "活动.."]) {
      await (await page.$("input")).input(keyword);
      await (await page.$(".search .primary")).tap();
      await ready(page);
      assert.ok(["ready", "empty"].includes(await page.data("state")));
    }
    evidence.searches = 4;
  } else {
    page = await mini.switchTab(
      mode === "offline" ? "/pages/home/index" : "/pages/registrations/index",
    );
    await ready(page);
    const data = await page.data();
    assert.equal(data.state, mode === "offline" ? "error" : "login");
    if (mode === "offline") {
      assert.match(data.error, /无法连接/);
      assert.equal(data.items.length, 0);
    } else {
      assert.equal(data.rows.length, 0);
      assert.equal(data.total, 0);
      assert.equal(data.feedback, "");
    }
    evidence.pageState = data.state;
  }
  await mini.screenshot({
    path: path.join(
      root,
      "docs/verification/m1.4-b-screenshots",
      `${mode}.png`,
    ),
  });
  evidence.status = "PASS";
})()
  .catch(() => {
    process.exitCode = 1;
  })
  .finally(async () => {
    fs.writeFileSync(
      path.join(root, `docs/verification/m1.4-b-runtime-${mode}.json`),
      JSON.stringify(evidence, null, 2),
    );
    console.log(JSON.stringify(evidence));
    if (mini) await mini.disconnect();
    clearTimeout(timer);
    process.exit(process.exitCode || 0);
  });
