const { test } = require("node:test");
const assert = require("node:assert/strict");
const { availability, beijing } = require("../dist/domain/activity");
test("Beijing time does not use host timezone", () => {
  assert.equal(beijing("2026-10-21T18:30:00Z"), "2026/10/22 02:30");
});
test("half-open registration window, full and ended states", () => {
  const a = {
    registrationStart: "2026-10-21T00:00:00Z",
    registrationEnd: "2026-10-21T02:00:00Z",
    startTime: "2026-10-21T03:00:00Z",
    endTime: "2026-10-21T04:00:00Z",
    remainingCapacity: 1,
  };
  assert.equal(
    availability(a, Date.parse(a.registrationStart) - 1).enabled,
    false,
  );
  assert.equal(availability(a, Date.parse(a.registrationStart)).enabled, true);
  assert.equal(
    availability(a, Date.parse(a.registrationEnd)).label,
    "报名已截止",
  );
  assert.equal(
    availability(
      { ...a, remainingCapacity: 0 },
      Date.parse(a.registrationStart),
    ).label,
    "名额已满",
  );
  assert.equal(availability(a, Date.parse(a.endTime)).label, "活动已结束");
});
