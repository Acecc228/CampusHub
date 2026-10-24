const { test } = require("node:test");
const assert = require("node:assert/strict");
const { createRegistrationActions } = require("../dist/domain/registration");
test("double click sends one mutation; timeout resolved by server read", async () => {
  let writes = 0;
  let release;
  const actions = createRegistrationActions({
    change: async () => {
      writes++;
      await new Promise((r) => (release = r));
      throw Error("timeout");
    },
    status: async () => ({ status: "CONFIRMED", version: 1 }),
  });
  const first = actions.change(1, 0, false);
  const second = actions.change(1, 0, false);
  release();
  const [a, b] = await Promise.all([first, second]);
  assert.equal(writes, 1);
  assert.equal(a.registration.status, "CONFIRMED");
  assert.equal(b.registration.status, "CONFIRMED");
  assert.equal(a.verified, true);
});
test("uncertain result locks writes until explicit successful status check", async () => {
  let writes = 0;
  let online = false;
  const actions = createRegistrationActions({
    change: async () => {
      writes++;
      throw Error("timeout");
    },
    status: async () => {
      if (!online) throw Error("offline");
      return { status: "CANCELLED", version: 2 };
    },
  });
  assert.equal((await actions.change(1, 0, false)).verified, false);
  assert.equal((await actions.change(1, 0, false)).verified, false);
  assert.equal(writes, 1);
  online = true;
  assert.equal((await actions.verify(1)).verified, true);
  await actions.change(1, 2, true);
  assert.equal(writes, 2);
});
test("timeout before commit plus old snapshot is still UNKNOWN until target or version advances", async () => {
  let committed = false;
  let writes = 0;
  const actions = createRegistrationActions({
    change: async () => {
      writes++;
      throw Error("timeout while backend still running");
    },
    status: async () =>
      committed
        ? { status: "CONFIRMED", version: 1 }
        : { status: "NONE", version: 0 },
  });
  assert.equal((await actions.change(8, 0, false)).verified, false);
  assert.equal((await actions.verify(8)).verified, false);
  await actions.change(8, 0, false);
  assert.equal(writes, 1);
  committed = true;
  assert.equal((await actions.verify(8)).verified, true);
});
test("identity reset prevents old read from locking or completing new user state", async () => {
  let resolve;
  const actions = createRegistrationActions({
    change: async () => ({ status: "CONFIRMED", version: 1 }),
    status: () => new Promise((r) => (resolve = r)),
  });
  const task = actions.change(8, 0, false);
  await new Promise((r) => setImmediate(r));
  actions.clear();
  resolve({ status: "CONFIRMED", version: 1 });
  const result = await task;
  assert.equal(result.verified, false);
  assert.equal(actions.isUncertain(8), false);
});

test("unpublished activity rejects registration and status with 404 without an unknown-result lock", async () => {
  let published = false;
  const rejection = { status: 404, code: "NOT_FOUND" };
  const actions = createRegistrationActions({
    change: async () => {
      if (!published) throw rejection;
      return { status: "CONFIRMED", version: 1 };
    },
    status: async () => {
      if (!published) throw { status: 404, code: "NOT_FOUND" };
      return { status: "CONFIRMED", version: 1 };
    },
  });
  const result = await actions.change(8, 0, false);
  assert.equal(result.verified, true);
  assert.equal(result.error, rejection);
  assert.equal(result.registration, undefined);
  assert.equal(actions.isUncertain(8), false);
  published = true;
  const retry = await actions.change(8, 0, false);
  assert.equal(retry.registration.status, "CONFIRMED");
});

test("5xx mutation followed by 404 status remains unknown and prevents replay", async () => {
  let writes = 0;
  const actions = createRegistrationActions({
    change: async () => {
      writes++;
      throw { status: 503 };
    },
    status: async () => {
      throw { status: 404 };
    },
  });
  assert.equal((await actions.change(8, 0, false)).verified, false);
  assert.equal(actions.isUncertain(8), true);
  assert.equal((await actions.change(8, 0, false)).verified, false);
  assert.equal(writes, 1);
});

test("identity reset during rejected mutation read cannot complete the old identity", async () => {
  let rejectRead;
  const actions = createRegistrationActions({
    change: async () => {
      throw { status: 404 };
    },
    status: () =>
      new Promise((_, reject) => {
        rejectRead = reject;
      }),
  });
  const pending = actions.change(8, 0, false);
  await new Promise((r) => setImmediate(r));
  actions.clear();
  rejectRead({ status: 404 });
  assert.equal((await pending).verified, false);
  assert.equal(actions.isUncertain(8), false);
});
