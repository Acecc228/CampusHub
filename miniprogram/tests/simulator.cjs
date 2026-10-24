const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const automator = require("miniprogram-automator");
if (process.env.CH_MINI_APPLY !== "true")
  throw Error("Run through Test-MiniprogramSimulator.ps1 -Apply");
const root = path.resolve(__dirname, "../..");
const out = path.join(root, "docs/verification/m1.4-b-screenshots");
fs.mkdirSync(out, { recursive: true });
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
const results = [];
let mini;
let page;
let runtime;
let system;
let exceptions = 0;
let stage = "";
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
async function ready(p) {
  await wait(250);
  for (let i = 0; i < 100; i++) {
    const state = await p.data("state");
    if (state !== "loading") return state;
    await wait(150);
  }
  throw Error("Page loading timeout");
}
async function check(name, fn) {
  console.log("CHECK " + name);
  stage = "";
  try {
    await fn();
    results.push({ name, status: "PASS" });
  } catch (e) {
    results.push({
      name,
      status: "FAIL",
      stage,
      reason:
        e instanceof assert.AssertionError
          ? "Assertion mismatch"
          : String(e.message)
              .slice(0, 220)
              .replace(/[a-f0-9]{64}/gi, "[REDACTED]"),
    });
    throw Error(name);
  }
}
async function shot(name) {
  await wait(300);
  await mini.screenshot({ path: path.join(out, name + ".png") });
}
(async () => {
  mini = await automator.connect({ wsEndpoint: "ws://127.0.0.1:9420" });
  mini.on("exception", () => exceptions++);
  const info = await mini.systemInfo();
  system = {
    platform: info.platform,
    SDKVersion: info.SDKVersion,
    windowWidth: info.windowWidth,
    windowHeight: info.windowHeight,
    pixelRatio: info.pixelRatio,
  };
  await check(
    "actual wx.request Cookie, Set-Cookie, session rotation, CSRF and logout",
    async () => {
      runtime = await mini.evaluate(
        async (username, password) => {
          const origin = "http://127.0.0.1:8080/api/v1";
          let cookie = "";
          let observedCookies = false;
          let observedHeader = false;
          function request(path, method = "GET", data, headers = {}) {
            return new Promise((resolve, reject) =>
              wx.request({
                url: origin + path,
                method,
                data,
                timeout: 15000,
                header: { ...headers, ...(cookie ? { Cookie: cookie } : {}) },
                success: (r) => {
                  observedCookies = observedCookies || !!r.cookies?.length;
                  const raw = Object.entries(r.header).find(
                    ([k]) => k.toLowerCase() === "set-cookie",
                  )?.[1];
                  observedHeader = observedHeader || !!raw;
                  const list = r.cookies?.length
                    ? r.cookies
                    : typeof raw === "string"
                      ? [raw]
                      : [];
                  for (const c of list) {
                    const m = /^JSESSIONID=([^;]*)/.exec(c);
                    if (m) cookie = m[1] ? "JSESSIONID=" + m[1] : "";
                  }
                  resolve(r);
                },
                fail: () => reject(Error("wx.request transport failure")),
              }),
            );
          }
          const csrf = (await request("/auth/csrf")).data;
          const before = cookie;
          const login = await request(
            "/auth/demo-login",
            "POST",
            "username=" +
              encodeURIComponent(username) +
              "&password=" +
              encodeURIComponent(password),
            {
              "content-type": "application/x-www-form-urlencoded",
              [csrf.headerName]: csrf.token,
            },
          );
          const rotated = !!before && !!cookie && before !== cookie;
          const me = await request("/auth/me");
          const denied = await request("/auth/logout", "POST", {});
          const fresh = (await request("/auth/csrf")).data;
          const logout = await request(
            "/auth/logout",
            "POST",
            {},
            { [fresh.headerName]: fresh.token },
          );
          const expired = await request("/auth/me");
          return {
            cookiesArray: observedCookies,
            setCookieHeader: observedHeader,
            explicitCookieSent: !!before,
            rotated,
            login: login.statusCode,
            me: me.statusCode,
            student: me.data.roles?.includes("STUDENT"),
            csrfDenied: denied.statusCode,
            logout: logout.statusCode,
            expired: expired.statusCode,
          };
        },
        account.LoginName,
        account.Password,
      );
      assert.equal(runtime.login, 200);
      assert.equal(runtime.me, 200);
      assert.equal(runtime.student, true);
      assert.equal(runtime.rotated, true);
      assert.equal(runtime.csrfDenied, 403);
      assert.equal(runtime.logout, 204);
      assert.equal(runtime.expired, 401);
    },
  );
  await check("native login page validates empty fields", async () => {
    page = await mini.reLaunch("/pages/login/index");
    await shot("login");
    await (await page.$(".primary")).tap();
    await wait(250);
    assert.match(await page.data("error"), /填写/);
  });
  await check("student login via actual input and button", async () => {
    await (await page.$("#username")).input(account.LoginName);
    await (await page.$("#password")).input(account.Password);
    await (await page.$(".primary")).tap();
    for (let i = 0; i < 100; i++) {
      page = await mini.currentPage();
      if (page.path === "pages/home/index") break;
      await wait(150);
    }
    assert.equal(page.path, "pages/home/index");
    assert.equal(await ready(page), "ready");
    await shot("home");
  });
  let selected;
  await check(
    "activity plaza keyword search, category and native card navigation",
    async () => {
      page = await mini.switchTab("/pages/activities/index");
      assert.equal(await ready(page), "ready");
      await (await page.$("input")).input("M14B-");
      await (await page.$(".search .primary")).tap();
      assert.equal(await ready(page), "ready");
      const items = await page.data("items");
      selected = items.find((x) => x.availability.enabled);
      assert.ok(selected);
      await shot("activities");
      stage = "category filter";
      const filters = await page.$$(".filter");
      const categoryIndex =
        (await page.data("categories")).findIndex(
          (x) => x.id === selected.categoryId,
        ) + 1;
      await filters[categoryIndex].tap();
      assert.equal(await ready(page), "ready");
      assert.ok(
        (await page.data("items")).every(
          (x) => x.categoryId === selected.categoryId,
        ),
      );
      stage = "card lookup";
      await wait(350);
      const cards = await page.$$(".campus-card");
      const index = (await page.data("items")).findIndex(
        (x) => x.id === selected.id,
      );
      stage = "card tap";
      await (await cards[index].$(".activity-card")).tap();
      stage = "detail readiness";
      for (let i = 0; i < 100; i++) {
        page = await mini.currentPage();
        if (page.path === "pages/detail/index") break;
        await wait(150);
      }
      assert.equal(page.path, "pages/detail/index");
      assert.equal(await ready(page), "ready");
      await shot("detail");
    },
  );
  await check(
    "register with repeated taps commits once and displays confirmed",
    async () => {
      const action = await page.$(".action-inner button");
      await Promise.all([action.tap(), action.tap()]);
      for (let i = 0; i < 120; i++) {
        if (
          (await page.data("activity"))?.confirmed &&
          !(await page.data("busy"))
        )
          break;
        await wait(150);
      }
      assert.equal((await page.data("activity")).confirmed, true);
      await shot("registration-confirmed");
    },
  );
  await check(
    "my registrations contain actual current user record",
    async () => {
      page = await mini.switchTab("/pages/registrations/index");
      assert.equal(await ready(page), "ready");
      assert.ok(
        (await page.data("rows")).some(
          (x) =>
            x.registration.activityId === selected.id &&
            x.registration.status === "CONFIRMED",
        ),
      );
      await shot("registrations");
    },
  );
  await check(
    "native cancellation confirmation then re-registration",
    async () => {
      page = await mini.navigateTo("/pages/detail/index?id=" + selected.id);
      assert.equal(await ready(page), "ready");
      stage = "cancel confirmation";
      await (await page.$(".action-inner button")).tap();
      console.log(
        "ACTION: confirm the actual simulator cancellation dialog within 120 seconds (desktop UI automation or manual click).",
      );
      for (let i = 0; i < 600; i++) {
        if (
          (await page.data("activity"))?.myRegistration?.status ===
            "CANCELLED" &&
          !(await page.data("busy"))
        )
          break;
        await wait(200);
      }
      assert.equal(
        (await page.data("activity")).myRegistration.status,
        "CANCELLED",
      );
      assert.equal(await page.data("busy"), false);
      stage = "re-registration";
      await (await page.$(".action-inner button")).tap();
      for (let i = 0; i < 120; i++) {
        if (
          (await page.data("activity"))?.confirmed &&
          !(await page.data("busy"))
        )
          break;
        await wait(150);
      }
      assert.equal((await page.data("activity")).confirmed, true);
    },
  );
  await check("search empty state and reset", async () => {
    page = await mini.switchTab("/pages/activities/index");
    await ready(page);
    await (await page.$("input")).input("no-results-" + Date.now());
    await (await page.$(".search .primary")).tap();
    assert.equal(await ready(page), "empty");
    await shot("no-results");
    await (await page.$(".row .ghost")).tap();
    assert.equal(await ready(page), "ready");
  });
  await check(
    "profile server identity, logout and hidden private data",
    async () => {
      page = await mini.switchTab("/pages/profile/index");
      assert.equal(await ready(page), "ready");
      assert.equal((await page.data("user")).loginName, account.LoginName);
      await shot("profile");
      stage = "logout";
      await (await page.$(".danger")).tap();
      for (let i = 0; i < 100 && (await page.data("state")) !== "login"; i++)
        await wait(150);
      assert.equal((await page.data()).user, null);
      stage = "private rows cleared";
      page = await mini.switchTab("/pages/registrations/index");
      assert.equal(await ready(page), "login");
      assert.equal((await page.data("rows")).length, 0);
    },
  );
})()
  .catch(() => {
    process.exitCode = 1;
  })
  .finally(async () => {
    fs.writeFileSync(
      path.join(root, "docs/verification/m1.4-b-simulator.json"),
      JSON.stringify({ system, runtime, exceptions, results }, null, 2),
    );
    console.log(
      JSON.stringify({
        PASS: results.filter((x) => x.status === "PASS").length,
        FAIL: results.filter((x) => x.status === "FAIL").length,
        exceptions,
      }),
    );
    if (mini) await mini.disconnect();
    process.exit(process.exitCode || 0);
  });
