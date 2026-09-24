import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { handleStatusChange, type Deps, type StatusChange } from "../handler.ts";
import { NotFoundError, RetriesExhaustedError } from "../partner.ts";
import { StatusTracker } from "../tracker.ts";

type Write = { serial: string; vehicleId: string; available: boolean };

let writes: Write[];
let failNext: Error | undefined;
let deps: Deps;

beforeEach(() => {
  writes = [];
  failNext = undefined;
  deps = {
    tracker: new StatusTracker(),
    partner: {
      lookupVehicleId: async (serial) => `veh_${serial}`,
      setAvailability: async (serial, vehicleId, available) => {
        if (failNext) {
          const err = failNext;
          failNext = undefined;
          throw err;
        }
        writes.push({ serial, vehicleId, available });
      },
    },
  };
});

function body(overrides: Partial<StatusChange> = {}): string {
  return JSON.stringify({
    serial: "C1",
    status: "ONLINE",
    limitingFactors: [],
    observedAt: "2026-09-24T17:00:00Z",
    ...overrides,
  });
}

test("writes availability and acks", async () => {
  assert.equal(await handleStatusChange("chg_1", body({ limitingFactors: ["LOW_BATTERY"] }), deps), "ack");
  assert.deepEqual(writes, [{ serial: "C1", vehicleId: "veh_C1", available: false }]);
});

test("a republished duplicate is acked without writing", async () => {
  await handleStatusChange("chg_1", body(), deps);
  assert.equal(await handleStatusChange("chg_1", body(), deps), "ack");
  assert.equal(writes.length, 1);
});

test("an older change arriving late is acked without writing", async () => {
  await handleStatusChange("chg_2", body({ status: "OFFLINE", observedAt: "2026-09-24T17:00:05Z" }), deps);
  assert.equal(await handleStatusChange("chg_1", body({ observedAt: "2026-09-24T17:00:00Z" }), deps), "ack");
  assert.deepEqual(writes, [{ serial: "C1", vehicleId: "veh_C1", available: false }]);
});

test("an unknown serial or vehicle is dead lettered", async () => {
  failNext = new NotFoundError("unknown");
  assert.equal(await handleStatusChange("chg_1", body(), deps), "nack");
});

test("exhausted retries requeue the message", async () => {
  failNext = new RetriesExhaustedError("gave up");
  assert.equal(await handleStatusChange("chg_1", body(), deps), "requeue");
});

test("a failed write is not recorded, so the republished copy is applied", async () => {
  failNext = new RetriesExhaustedError("gave up");
  await handleStatusChange("chg_1", body(), deps);
  assert.equal(await handleStatusChange("chg_1", body(), deps), "ack");
  assert.equal(writes.length, 1);
});

test("malformed messages are dead lettered", async () => {
  assert.equal(await handleStatusChange("chg_1", "not json", deps), "nack");
  assert.equal(await handleStatusChange("chg_1", body({ status: "SLEEPING" as never }), deps), "nack");
  assert.equal(await handleStatusChange("chg_1", body({ observedAt: "yesterday" }), deps), "nack");
  assert.equal(await handleStatusChange(undefined, body(), deps), "nack");
  assert.equal(writes.length, 0);
});
