const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createClient, ApiError } = require("../dist/services/client");
function harness(responses) {
  const calls = [];
  const client = createClient(async (input) => {
    calls.push(input);
    const next = responses.shift();
    if (next instanceof Error) throw next;
    return { statusCode: 200, header: {}, data: {}, ...next };
  });
  return { client, calls };
}
test("explicit JSESSIONID from cookies, rotate after login and fetch fresh CSRF", async () => {
  const { client, calls } = harness([
    {
      data: { headerName: "X-CSRF-TOKEN", token: "before" },
      cookies: ["JSESSIONID=old; Path=/; HttpOnly"],
    },
    { cookies: ["JSESSIONID=new; Path=/; HttpOnly"] },
    { data: { headerName: "X-CSRF-TOKEN", token: "after" } },
    {},
  ]);
  await client.request(
    "/auth/demo-login",
    "POST",
    "username=a&password=b",
    true,
  );
  await client.request("/auth/logout", "POST");
  assert.equal(calls[1].header.Cookie, "JSESSIONID=old");
  assert.equal(calls[1].header["X-CSRF-TOKEN"], "before");
  assert.equal(calls[2].header.Cookie, "JSESSIONID=new");
  assert.equal(calls[3].header["X-CSRF-TOKEN"], "after");
});
test("case insensitive Set-Cookie fallback, only session forwarded, deletion respected", async () => {
  const { client, calls } = harness([
    {
      header: {
        "sEt-CoOkIe": "JSESSIONID=abc; Path=/; HttpOnly, other=secret; Path=/",
      },
    },
    { cookies: ["JSESSIONID=; Max-Age=0; Path=/"] },
    {},
  ]);
  await client.request("/auth/me");
  await client.request("/auth/me");
  await client.request("/auth/me");
  assert.equal(calls[1].header.Cookie, "JSESSIONID=abc");
  assert.equal(calls[2].header.Cookie, undefined);
});
test("401 clears credentials and signals invalidation, 403 does not logout", async () => {
  let expired = 0;
  const { client, calls } = harness([
    { cookies: ["JSESSIONID=abc"] },
    { statusCode: 403 },
    { statusCode: 401 },
    {},
  ]);
  client.onUnauthorized = () => expired++;
  await client.request("/auth/me");
  await assert.rejects(
    client.request("/student/access"),
    (e) => e.status === 403,
  );
  assert.equal(expired, 0);
  await assert.rejects(client.request("/auth/me"), (e) => e.status === 401);
  assert.equal(expired, 1);
  await client.request("/auth/me");
  assert.equal(calls[3].header.Cookie, undefined);
});
test("POST network timeout is unknown and never automatically retried", async () => {
  const { client, calls } = harness([
    { data: { headerName: "X-CSRF-TOKEN", token: "t" } },
    new ApiError(0, "TIMEOUT", "超时"),
  ]);
  await assert.rejects(
    client.request("/activities/1/registrations", "POST", { version: 0 }),
    (e) => e.code === "TIMEOUT",
  );
  assert.equal(calls.length, 2);
});
test("reject absolute or traversing URL before sending credentials", async () => {
  const { client, calls } = harness([]);
  for (const path of [
    "https://evil.test",
    "//evil",
    "/../auth/me",
    "/auth/me\r\n",
  ])
    await assert.rejects(client.request(path));
  assert.equal(calls.length, 0);
});
test("clearing session invalidates an in-flight response", async () => {
  let resolve;
  const client = createClient(() => new Promise((r) => (resolve = r)));
  const request = client.request("/auth/me");
  await new Promise((r) => setImmediate(r));
  client.clear();
  resolve({
    statusCode: 200,
    header: {},
    cookies: ["JSESSIONID=stale"],
    data: { id: 1 },
  });
  await assert.rejects(request, (e) => e.code === "STALE_SESSION");
});

for (const keyword of ["O'Reilly", "活动 (线上)", "你好!", "活动.."])
  test(`encoded search preserves legitimate punctuation: ${keyword}`, async () => {
    const { client, calls } = harness([{}]);
    await client.request("/activities?keyword=" + encodeURIComponent(keyword));
    assert.equal(calls.length, 1);
    assert.equal(new URL(calls[0].url).searchParams.get("keyword"), keyword);
  });
