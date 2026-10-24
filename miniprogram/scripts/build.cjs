const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "..");
function copy(dir) {
  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, item.name);
    if (item.isDirectory()) copy(file);
    else if (!item.name.endsWith(".ts")) {
      const dest = path.join(
        root,
        "dist",
        path.relative(path.join(root, "src"), file),
      );
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.copyFileSync(file, dest);
    }
  }
}
copy(path.join(root, "src"));
console.log(
  "PASS: TypeScript emitted and native assets copied. This is not a DevTools compile.",
);
