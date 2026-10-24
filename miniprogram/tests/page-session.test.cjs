const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

for (const [name, method] of [
  ["detail", "act"],
  ["detail", "verify"],
  ["registrations", "cancel"],
]) {
  test(`${name}.${method} cannot restore feedback after refresh expires the session`, async () => {
    let definition;
    const toasts = [];
    const session = { generation: 1, user: { id: 1 } };
    const result = {
      verified: true,
      registration: { status: method === "act" ? "CONFIRMED" : "CANCELLED" },
    };
    const modules = {
      "../../services/api": {
        registrationActions: {
          change: async () => result,
          verify: async () => result,
        },
      },
      "../../services/session": { session },
      "../../domain/activity": {},
      "../../services/client": {},
      "../../ui/navigation": {
        confirmCancel: async () => true,
        toast: (message) => toasts.push(message),
      },
    };
    vm.runInNewContext(
      fs.readFileSync(
        path.join(__dirname, `../dist/pages/${name}/index.js`),
        "utf8",
      ),
      {
        exports: {},
        require: (id) => modules[id],
        Page: (value) => {
          definition = value;
        },
      },
    );
    const page = {
      ...definition,
      data: {
        ...definition.data,
        activity: { title: "Activity", confirmed: false },
        rows: [
          { registration: { activityId: 1, version: 1 }, title: "Activity" },
        ],
      },
      setData(value) {
        Object.assign(this.data, value);
      },
      async load() {
        session.generation++;
        session.user = null;
        this.setData({ state: "login", feedback: "", busy: false, busyId: 0 });
      },
    };
    await page[method]({ currentTarget: { dataset: { id: 1 } } });
    assert.equal(page.data.state, "login");
    assert.equal(page.data.feedback, "");
    assert.equal(toasts.length, 0);
  });
}
