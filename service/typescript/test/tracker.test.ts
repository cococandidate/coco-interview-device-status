import assert from "node:assert/strict";
import { test } from "node:test";
import { StatusTracker } from "../tracker.ts";

test("an unseen message is new", () => {
  assert.equal(new StatusTracker().check("chg_1", "C1", 1000), "new");
});

test("a recorded message id is a duplicate", () => {
  const t = new StatusTracker();
  t.record("chg_1", "C1", 1000);
  assert.equal(t.check("chg_1", "C1", 1000), "duplicate");
});

test("a message is not a duplicate until it has been recorded", () => {
  const t = new StatusTracker();
  t.check("chg_1", "C1", 1000);
  assert.equal(t.check("chg_1", "C1", 1000), "new");
});

test("an older change for the same serial is stale", () => {
  const t = new StatusTracker();
  t.record("chg_2", "C1", 2000);
  assert.equal(t.check("chg_1", "C1", 1000), "stale");
});

test("a change with the same observedAt as the applied one is stale", () => {
  const t = new StatusTracker();
  t.record("chg_2", "C1", 2000);
  assert.equal(t.check("chg_3", "C1", 2000), "stale");
});

test("a newer change for the same serial is new", () => {
  const t = new StatusTracker();
  t.record("chg_1", "C1", 1000);
  assert.equal(t.check("chg_2", "C1", 2000), "new");
});

test("ordering is tracked per serial", () => {
  const t = new StatusTracker();
  t.record("chg_2", "C1", 2000);
  assert.equal(t.check("chg_1", "C2", 1000), "new");
});

test("recording an older change does not move the latest back", () => {
  const t = new StatusTracker();
  t.record("chg_2", "C1", 2000);
  t.record("chg_1", "C1", 1000);
  assert.equal(t.latestFor("C1"), 2000);
});
