// Production client/domain with real HTTP and MySQL; Node transport, not wx.request UI evidence.
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");
const { createClient, errorMessage } = require("../dist/services/client");
const { createRegistrationActions } = require("../dist/domain/registration");
if (process.env.CH_C02_APPLY !== "true") {
  console.log("PREVIEW: use scripts/Test-C02Client.ps1 -Apply");
} else {
  const report = {
    status: "RUNNING",
    transport: "Node fetch -> real Spring Boot/MySQL",
    checks: [],
  };
  const destination = path.resolve(
    __dirname,
    "../../docs/verification/m1.4-c-02/c01-client.json",
  );
  // Fail before writes if evidence would be overwritten.
  const output = fs.openSync(destination, "wx");
  const secrets = JSON.parse(
    fs
      .readFileSync(
        "E:/CampusHubRuntime/mysql84/secrets/campushub_test-demo.json",
        "utf8",
      )
      .replace(/^\uFEFF/, ""),
  );
  assert.equal(secrets.Database, "campushub_test");
  let dropResponse = false;
  let blockStatus = false;
  let mutations = 0;
  async function transport(input) {
    if (blockStatus && /\/registration$/.test(input.url))
      throw Error("controlled transport outage");
    const response = await fetch(input.url, {
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
    if (
      /\/registrations(?:\/cancel)?$/.test(input.url) &&
      input.method === "POST"
    ) {
      mutations++;
      if (dropResponse) {
        assert.equal(response.status, 200);
        // A real committed write whose response is deliberately lost to the client.
        throw Error("controlled loss after actual commit");
      }
    }
    return {
      statusCode: response.status,
      header: Object.fromEntries(response.headers),
      cookies: response.headers.getSetCookie(),
      data: response.status === 204 ? undefined : await response.json(),
    };
  }
  const student = createClient(transport),
    manager = createClient(transport);
  async function login(client, role) {
    const account = secrets.Accounts.find(
      (a) => a.LoginName === "demo_" + role,
    );
    return client.request(
      "/auth/demo-login",
      "POST",
      "username=" +
        account.LoginName +
        "&password=" +
        encodeURIComponent(account.Password),
      true,
    );
  }
  (async () => {
    const identity = await login(student, "student");
    await login(manager, "organizer");
    report.studentId = identity.id;
    const category = (await manager.request("/activity-categories"))[0].id;
    const iso = (seconds) =>
      new Date(Math.floor(Date.now() / 1000) * 1000 + seconds * 1000)
        .toISOString()
        .replace(".000Z", "Z");
    const activity = await manager.request("/manage/activities", "POST", {
      title: "M14C02-" + Date.now(),
      summary: "C-01 定向回归",
      content: "真实客户端与数据库验证",
      categoryId: category,
      location: "测试场地",
      startTime: iso(172800),
      endTime: iso(176400),
      registrationStart: iso(-60),
      registrationEnd: iso(86400),
      capacity: 2,
    });
    report.activityId = activity.id;
    const manage = "/manage/activities/" + activity.id;
    const publicPath = "/activities/" + activity.id;
    await manager.request(manage + "/publish", "POST", { version: 0 });
    await manager.request(manage + "/unpublish", "POST", { version: 1 });
    const actions = createRegistrationActions({
      change: (id, version, cancel) =>
        student.request(
          "/activities/" + id + "/registrations" + (cancel ? "/cancel" : ""),
          "POST",
          { version },
        ),
      status: (id) => student.request("/activities/" + id + "/registration"),
    });
    const rejected = await actions.change(activity.id, 0, false);
    assert.equal(rejected.verified, true);
    assert.equal(rejected.error.status, 404);
    assert.equal(rejected.error.code, "NOT_FOUND");
    assert.ok(errorMessage(rejected.error));
    assert.equal(actions.isUncertain(activity.id), false);
    const roster = await manager.request(manage + "/registrations");
    assert.equal(roster.total, 0);
    assert.equal((await manager.request(manage)).registeredCount, 0);
    report.checks.push({
      name: "explicit 404 and failed status read show error without unknown lock; empty real roster/count",
      status: "PASS",
    });
    await manager.request(manage + "/publish", "POST", { version: 2 });
    assert.equal((await student.request(publicPath)).remainingCapacity, 2);
    const retried = await actions.change(activity.id, 0, false);
    assert.equal(retried.verified, true);
    assert.equal(retried.registration.status, "CONFIRMED");
    report.checks.push({
      name: "refresh after republish and same actions instance can register",
      status: "PASS",
    });
    dropResponse = true;
    blockStatus = true;
    assert.equal((await actions.change(activity.id, 1, true)).verified, false);
    const writes = mutations;
    assert.equal((await actions.change(activity.id, 1, true)).verified, false);
    assert.equal(mutations, writes);
    dropResponse = false;
    blockStatus = false;
    const recovered = await actions.verify(activity.id);
    assert.equal(recovered.verified, true);
    assert.equal(recovered.registration.status, "CANCELLED");
    assert.equal(recovered.registration.version, 2);
    const detail = await manager.request(manage);
    assert.equal(detail.registeredCount, 0);
    assert.equal(detail.remainingCapacity, 2);
    report.checks.push({
      name: "actual committed cancellation with injected response/read loss blocks replay then recovers via real status",
      status: "PASS",
    });
    assert.equal((await manager.request("/auth/me")).roles[0], "ORGANIZER");
    assert.equal((await student.request("/auth/me")).id, identity.id);
    report.checks.push({
      name: "student and manager sessions isolated",
      status: "PASS",
    });
    await student.request("/auth/logout", "POST");
    await manager.request("/auth/logout", "POST");
    report.status = "PASS";
  })()
    .catch((error) => {
      report.status = "FAIL";
      report.failure = {
        type: error.name,
        code: error.code || "ASSERTION",
        message: "Raw error suppressed; inspect test assertion",
      };
      process.exitCode = 1;
    })
    .finally(() => {
      report.at = new Date().toISOString();
      fs.writeFileSync(output, JSON.stringify(report, null, 2) + "\n");
      fs.closeSync(output);
      console.log(JSON.stringify(report));
    });
}
