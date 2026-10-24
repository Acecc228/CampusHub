import { readFileSync } from "node:fs";
import { expect, it } from "vitest";
it("DESIGN 镜像的核心颜色与运行时单一令牌源一致", () => {
  const design = readFileSync("../DESIGN.md", "utf8");
  const tokens = readFileSync("src/styles/tokens.css", "utf8");
  for (const match of design.matchAll(
    /^ {2}(primary|background|surface|text|muted|border|success|danger): "(#[a-f0-9]+)"/gm,
  ))
    expect(tokens).toContain(`--ch-${match[1]}: ${match[2]}`);
});
