// Runs the production client with a Node HTTP adapter. This is NOT wx.request runtime evidence.
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { createClient, ApiError } = require("../dist/services/client");
if (process.env.CH_MINI_APPLY !== "true")
  throw Error("Use scripts/Test-Miniprogram.ps1 -Apply");
const secrets = JSON.parse(
  fs
    .readFileSync(
      "E:/CampusHubRuntime/mysql84/secrets/campushub_test-demo.json",
      "utf8",
    )
    .replace(/^\uFEFF/, ""),
);
assert.equal(secrets.Database, "campushub_test");
const results = [];
const ids = [];
const prefix = "M14B-" + Date.now();
async function transport(input) {
  const r = await fetch(input.url, {
    method: input.method,
    headers: input.header,
    body:
      input.method === "POST"
        ? typeof input.data === "string"
          ? input.data
          : JSON.stringify(input.data)
        : undefined,
    signal: AbortSignal.timeout(input.timeout),
  });
  return {
    statusCode: r.status,
    header: Object.fromEntries(r.headers),
    cookies: r.headers.getSetCookie(),
    data: r.status === 204 ? undefined : await r.json(),
  };
}
function make() {
  return createClient(transport);
}
async function login(client, role, name = "demo_" + role) {
  const pwd = secrets.Accounts.find(
    (a) => a.LoginName === "demo_" + role,
  ).Password;
  await client.request(
    "/auth/demo-login",
    "POST",
    "username=" +
      encodeURIComponent(name) +
      "&password=" +
      encodeURIComponent(pwd),
    true,
  );
  return client.request("/auth/me");
}
async function check(name, fn) {
  try {
    await fn();
    results.push({ name, status: "PASS" });
  } catch (e) {
    results.push({
      name,
      status: "FAIL",
      code: e instanceof ApiError ? e.code : "ASSERTION",
      message:
        e instanceof assert.AssertionError
          ? "Assertion mismatch; inspect test contract"
          : "Operation failed; sensitive error suppressed",
    });
    throw Error(name);
  }
}
async function expectStatus(p, status) {
  await assert.rejects(p, (e) => e.status === status);
}
const report = path.resolve(
  __dirname,
  "../../docs/verification/m1.4-b-real-api.json",
);
(async () => {
  const student = make(),
    other = make(),
    manager = make();
  let identity, category, activity, closed, draft;
  await check("unauthenticated list and own registrations denied", async () => {
    await expectStatus(student.request("/activities"), 401);
    await expectStatus(student.request("/student/registrations"), 401);
  });
  await check(
    "student login and session survives consecutive requests",
    async () => {
      identity = await login(student, "student");
      assert.ok(identity.roles.includes("STUDENT"));
      assert.equal((await student.request("/auth/me")).id, identity.id);
    },
  );
  await check("manager and isolated second student login", async () => {
    await login(manager, "organizer");
    await login(other, "student", process.env.CH_MINI_STUDENT);
  });
  await check("real categories", async () => {
    const c = await student.request("/activity-categories");
    assert.ok(c.length > 0);
    category = c[0].id;
  });
  const iso = (n) =>
    new Date(Math.floor(Date.now() / 1000) * 1000 + n * 1000)
      .toISOString()
      .replace(".000Z", "Z");
  async function create(label, cap = 5, deadline = 3600) {
    const a = await manager.request("/manage/activities", "POST", {
      title: prefix + " " + label,
      summary: "原生学生端真实联调验收活动",
      content:
        "这是一条标明用途的真实测试活动。\n所有报名写入 campushub_test。",
      categoryId: category,
      location: "校园活动中心 201",
      registrationStart: iso(-3600),
      registrationEnd: iso(deadline),
      startTime: iso(7200),
      endTime: iso(10800),
      capacity: cap,
    });
    ids.push(a.id);
    return a;
  }
  async function publish(a) {
    return manager.request("/manage/activities/" + a.id + "/publish", "POST", {
      version: a.version,
    });
  }
  await check("create isolated real test activities", async () => {
    activity = await publish(await create("开放活动", 1));
    draft = await create("未公开草稿");
  });
  await check("home latest list, keyword, category and paging", async () => {
    const p = await student.request(
      "/activities?page=1&size=1&categoryId=" +
        category +
        "&keyword=" +
        encodeURIComponent(prefix),
    );
    assert.equal(p.items.length, 1);
    assert.equal(p.items[0].id, activity.id);
    assert.equal(p.total, 1);
    assert.equal(
      (await student.request("/activities?page=2&size=1&keyword=" + prefix))
        .items.length,
      0,
    );
    assert.equal(
      (await student.request("/activities?keyword=not-found-" + Date.now()))
        .total,
      0,
    );
  });
  await check("detail and initial own registration", async () => {
    const d = await student.request("/activities/" + activity.id);
    assert.equal(d.myRegistration.status, "NONE");
    assert.equal(d.remainingCapacity, 1);
  });
  await check("CSRF required even with valid session", async () => {
    let saved;
    const probe = createClient(async (i) => {
      const r = await transport(i);
      saved = { ...i.header };
      return r;
    });
    await login(probe, "student");
    const r = await transport({
      url:
        "http://127.0.0.1:8080/api/v1/activities/" +
        activity.id +
        "/registrations",
      method: "POST",
      header: { Cookie: saved.Cookie, "content-type": "application/json" },
      data: { version: 0 },
      timeout: 15000,
    });
    assert.equal(r.statusCode, 403);
  });
  let reg;
  await check("normal and repeated registration stays one row", async () => {
    reg = await student.request(
      "/activities/" + activity.id + "/registrations",
      "POST",
      { version: 0 },
    );
    const twice = await student.request(
      "/activities/" + activity.id + "/registrations",
      "POST",
      { version: 0 },
    );
    assert.equal(reg.id, twice.id);
    assert.equal(
      (await student.request("/activities/" + activity.id)).registeredCount,
      1,
    );
  });
  await check("full capacity rejects second student", async () => {
    await assert.rejects(
      other.request("/activities/" + activity.id + "/registrations", "POST", {
        version: 0,
      }),
      (e) => e.code === "ACTIVITY_FULL",
    );
  });
  await check("own registrations cannot impersonate another user", async () => {
    const mine = await student.request("/student/registrations?size=100");
    assert.ok(mine.items.some((x) => x.id === reg.id));
    assert.ok(mine.items.every((x) => x.userId === identity.id));
    const theirs = await other.request(
      "/student/registrations?userId=" + identity.id,
    );
    assert.equal(theirs.total, 0);
  });
  await check(
    "cancel and register again reuse history with newer version",
    async () => {
      const c = await student.request(
        "/activities/" + activity.id + "/registrations/cancel",
        "POST",
        { version: reg.version },
      );
      assert.equal(c.status, "CANCELLED");
      reg = await student.request(
        "/activities/" + activity.id + "/registrations",
        "POST",
        { version: c.version },
      );
      assert.equal(reg.id, c.id);
      assert.equal(reg.status, "CONFIRMED");
      assert.ok(reg.version > c.version);
    },
  );
  await check(
    "stale cancel conflicts and does not cancel new registration",
    async () => {
      await assert.rejects(
        student.request(
          "/activities/" + activity.id + "/registrations/cancel",
          "POST",
          { version: 0 },
        ),
        (e) => e.code === "REGISTRATION_CONFLICT",
      );
      assert.equal(
        (await student.request("/activities/" + activity.id + "/registration"))
          .status,
        "CONFIRMED",
      );
    },
  );
  await check("draft hidden and student management access denied", async () => {
    await expectStatus(student.request("/activities/" + draft.id), 404);
    await expectStatus(student.request("/manage/activities"), 403);
    await expectStatus(manager.request("/student/registrations"), 403);
  });
  await check(
    "unpublished detail hidden; own record and cancellation retained",
    async () => {
      await manager.request(
        "/manage/activities/" + activity.id + "/unpublish",
        "POST",
        { version: activity.version },
      );
      await expectStatus(student.request("/activities/" + activity.id), 404);
      assert.equal(
        (await student.request("/activities/" + activity.id + "/registration"))
          .status,
        "CONFIRMED",
      );
      reg = await student.request(
        "/activities/" + activity.id + "/registrations/cancel",
        "POST",
        { version: reg.version },
      );
      assert.equal(reg.status, "CANCELLED");
    },
  );
  await check("deadline enforced by server clock", async () => {
    closed = await publish(await create("截止活动", 5, 4));
    await new Promise((r) => setTimeout(r, 4200));
    await assert.rejects(
      student.request("/activities/" + closed.id + "/registrations", "POST", {
        version: 0,
      }),
      (e) => e.code === "REGISTRATION_CLOSED",
    );
  });
  await check("lost response resolved by actual server state", async () => {
    const a = await publish(await create("超时核实"));
    let lose = false;
    const client = createClient(async (input) => {
      const result = await transport(input);
      if (
        lose &&
        input.method === "POST" &&
        input.url.endsWith("/registrations")
      )
        throw new ApiError(
          0,
          "TIMEOUT",
          "Simulated lost response AFTER real commit",
        );
      return result;
    });
    await login(client, "student");
    lose = true;
    const {
      createRegistrationActions,
    } = require("../dist/domain/registration");
    const actions = createRegistrationActions({
      change: (id, version) =>
        client.request("/activities/" + id + "/registrations", "POST", {
          version,
        }),
      status: (id) => client.request("/activities/" + id + "/registration"),
    });
    const r = await actions.change(a.id, 0, false);
    assert.equal(r.verified, true);
    assert.equal(r.registration.status, "CONFIRMED");
  });
  await check("logout destroys session and replay fails", async () => {
    let captured;
    const client = createClient(async (input) => {
      captured = input.header.Cookie;
      return transport(input);
    });
    await login(client, "student");
    const old = captured;
    await client.request("/auth/logout", "POST");
    const replay = await transport({
      url: "http://127.0.0.1:8080/api/v1/auth/me",
      method: "GET",
      header: { Cookie: old },
      timeout: 15000,
    });
    assert.equal(replay.statusCode, 401);
    await expectStatus(client.request("/auth/me"), 401);
  });
  // Keep a published open activity for the simulator; test fixtures are explicitly labelled.
  for (const [title, location, content] of [
    [
      "把周末交给舞台 · 校园音乐分享会",
      "大学生活动中心 · 小剧场",
      "带上你喜欢的一首歌，认识同样热爱音乐的同学。\n活动安排：签到入场、作品分享、自由交流。",
    ],
    [
      "灵感碰撞 · 青年创作工作坊",
      "图书馆 · 共享创作空间",
      "从一个校园观察开始，把想法变成可分享的小作品。\n无需经验，欢迎不同专业同学参与。",
    ],
    [
      "开放日 · 从这里认识校园",
      "校园活动中心 · 201",
      "一起探索校园里的学习与生活空间，认识新的同行者。\n活动安排：校园导览、社团交流、小组讨论。",
    ],
  ]) {
    const a = await manager.request("/manage/activities", "POST", {
      title,
      summary: "本地演示活动 · " + prefix,
      content:
        content + "\n\n此活动为 CampusHub 本地联调演示数据，非真实校园公告。",
      categoryId: category,
      location,
      registrationStart: iso(-3600),
      registrationEnd: iso(3 * 86400),
      startTime: iso(7 * 86400),
      endTime: iso(7 * 86400 + 7200),
      capacity: 30,
    });
    ids.push(a.id);
    await publish(a);
  }
})()
  .catch(() => {
    process.exitCode = 1;
  })
  .finally(() => {
    fs.writeFileSync(
      report,
      JSON.stringify(
        {
          adapter: "Node fetch using production client; not wx.request runtime",
          database: "campushub_test",
          prefix,
          activityIds: ids,
          results,
        },
        null,
        2,
      ),
    );
    console.log(
      JSON.stringify({
        PASS: results.filter((x) => x.status === "PASS").length,
        FAIL: results.filter((x) => x.status === "FAIL").length,
        report,
      }),
    );
  });
