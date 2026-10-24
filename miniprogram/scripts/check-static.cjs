const fs = require("node:fs");
const path = require("node:path");
const { execFileSync } = require("node:child_process");

const root = path.resolve(__dirname, "..");
let checked = 0;

function visit(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name);
    if (entry.isDirectory()) visit(file);
    else if (file.endsWith(".cjs")) {
      execFileSync(process.execPath, ["--check", file], { stdio: "pipe" });
      checked++;
    } else if (file.endsWith(".json")) {
      JSON.parse(fs.readFileSync(file, "utf8"));
      checked++;
    }
  }
}

visit(path.join(root, "scripts"));
visit(path.join(root, "tests"));
visit(path.join(root, "src"));
JSON.parse(fs.readFileSync(path.join(root, "project.config.json"), "utf8"));
console.log(`PASS: ${checked} JavaScript/JSON files and project.config.json parsed.`);
