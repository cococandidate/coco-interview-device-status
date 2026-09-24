import assert from "node:assert/strict";
import { test } from "node:test";
import { BLOCKING_FACTORS, isAvailable } from "../availability.ts";

test("online with no limiting factors is available", () => {
  assert.equal(isAvailable({ status: "ONLINE", limitingFactors: [] }), true);
});

test("offline is never available", () => {
  assert.equal(isAvailable({ status: "OFFLINE", limitingFactors: [] }), false);
});

for (const factor of BLOCKING_FACTORS) {
  test(`${factor} blocks availability`, () => {
    assert.equal(isAvailable({ status: "ONLINE", limitingFactors: [factor] }), false);
  });
}

test("informational factors do not block availability", () => {
  assert.equal(isAvailable({ status: "ONLINE", limitingFactors: ["LID_OPEN", "CHARGING"] }), true);
});

test("one blocking factor among informational ones blocks", () => {
  assert.equal(isAvailable({ status: "ONLINE", limitingFactors: ["LID_OPEN", "OUT_OF_ZONE"] }), false);
});
