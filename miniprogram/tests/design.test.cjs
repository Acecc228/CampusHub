const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../..");
test("mobile semantic palette remains aligned with accepted Indigo brand", () => {
  const tokens = fs.readFileSync(
    path.join(root, "miniprogram/src/styles/tokens.wxss"),
    "utf8",
  );
  const design = fs.readFileSync(path.join(root, "DESIGN.md"), "utf8");
  const names = {
    primary: "#4f46e5",
    background: "#f6f7fb",
    surface: "#ffffff",
    text: "#182239",
    muted: "#606b80",
    border: "#e4e7ef",
    success: "#187658",
    danger: "#b42318",
  };
  for (const [name, color] of Object.entries(names)) {
    assert.ok(tokens.includes("--ch-" + name + ": " + color));
    assert.ok(design.includes(color));
  }
  const app = JSON.parse(
    fs.readFileSync(path.join(root, "miniprogram/src/app.json"), "utf8"),
  );
  assert.equal(app.tabBar.selectedColor, names.primary);
  assert.equal(app.tabBar.list.length, 4);
  for (const item of app.tabBar.list)
    for (const key of ["iconPath", "selectedIconPath"])
      assert.ok(fs.existsSync(path.join(root, "miniprogram/src", item[key])));
});
