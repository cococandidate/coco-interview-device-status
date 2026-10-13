import { test } from "node:test";
import assert from "node:assert/strict";

import { config, get } from "../../server.ts";

test("given a robot registered with HangryHippo, when the fleet is asked for its HangryHippo account, then it returns the vehicle id", async () => {
  const res = await get(`${config.fleetUrl}/v1/devices/C10393/partner-accounts/hangry-hippo`);

  assert.deepEqual(res, { status: 200, body: { serial: "C10393", partner: "hangry-hippo", vehicleId: "veh_8f21c4" } });
});
