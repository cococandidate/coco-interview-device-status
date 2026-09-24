import assert from "node:assert/strict";
import { test } from "node:test";
import type { HttpResponse } from "../http.ts";
import { NotFoundError, PartnerClient, RetriesExhaustedError, retryAfterMs, type Http } from "../partner.ts";

type Reply = HttpResponse | Error;

function res(status: number, body?: unknown, headers: Record<string, string> = {}): HttpResponse {
  return { status, headers: new Headers(headers), body };
}

// Replays the given replies in order and records every request.
function fakeHttp(replies: Reply[]) {
  const calls: string[] = [];
  const next = async (label: string) => {
    calls.push(label);
    const reply = replies.shift();
    if (reply === undefined) throw new Error(`unexpected request ${label}`);
    if (reply instanceof Error) throw reply;
    return reply;
  };
  const http: Http = {
    get: (url) => next(`GET ${url}`),
    put: (url, body) => next(`PUT ${url} ${JSON.stringify(body)}`),
  };
  return { http, calls };
}

function client(replies: Reply[]) {
  const { http, calls } = fakeHttp(replies);
  const sleeps: number[] = [];
  const partner = new PartnerClient("http://partner", http, undefined, async (ms) => {
    sleeps.push(ms);
  });
  return { partner, calls, sleeps };
}

test("looks up the vehicle id and caches it", async () => {
  const { partner, calls } = client([res(200, { serial: "C1", vehicleId: "veh_1" })]);
  assert.equal(await partner.lookupVehicleId("C1"), "veh_1");
  assert.equal(await partner.lookupVehicleId("C1"), "veh_1");
  assert.deepEqual(calls, ["GET http://partner/v1/vehicles?serial=C1"]);
});

test("an unregistered serial is a NotFoundError", async () => {
  const { partner } = client([res(404)]);
  await assert.rejects(partner.lookupVehicleId("C1"), NotFoundError);
});

test("writes availability", async () => {
  const { partner, calls } = client([res(204)]);
  await partner.setAvailability("C1", "veh_1", true);
  assert.deepEqual(calls, ['PUT http://partner/v1/vehicles/veh_1/availability {"available":true}']);
});

test("a 404 on write is a NotFoundError and evicts the cached vehicle id", async () => {
  const { partner, calls } = client([
    res(200, { vehicleId: "veh_old" }),
    res(404),
    res(200, { vehicleId: "veh_new" }),
  ]);
  const vehicleId = await partner.lookupVehicleId("C1");
  await assert.rejects(partner.setAvailability("C1", vehicleId, true), NotFoundError);
  assert.equal(await partner.lookupVehicleId("C1"), "veh_new");
  assert.equal(calls.filter((c) => c.startsWith("GET")).length, 2);
});

test("a 503 is retried with exponential backoff", async () => {
  const { partner, sleeps } = client([res(503), res(503), res(204)]);
  await partner.setAvailability("C1", "veh_1", false);
  assert.deepEqual(sleeps, [500, 1000]);
});

test("Retry-After is used instead of the backoff when present", async () => {
  const { partner, sleeps } = client([res(503, undefined, { "Retry-After": "2" }), res(204)]);
  await partner.setAvailability("C1", "veh_1", false);
  assert.deepEqual(sleeps, [2000]);
});

test("gives up after five attempts", async () => {
  const { partner, calls, sleeps } = client([res(503), res(503), res(503), res(503), res(503)]);
  await assert.rejects(partner.setAvailability("C1", "veh_1", false), RetriesExhaustedError);
  assert.equal(calls.length, 5);
  assert.deepEqual(sleeps, [500, 1000, 2000, 4000]);
});

test("gives up rather than wait past the retry budget", async () => {
  const { partner, calls, sleeps } = client([res(503, undefined, { "Retry-After": "30" })]);
  await assert.rejects(partner.setAvailability("C1", "veh_1", false), RetriesExhaustedError);
  assert.equal(calls.length, 1);
  assert.deepEqual(sleeps, []);
});

test("transport errors are retried", async () => {
  const timeout = new DOMException("The operation was aborted due to timeout", "TimeoutError");
  const { partner, sleeps } = client([timeout, new TypeError("fetch failed"), res(200, { vehicleId: "veh_1" })]);
  assert.equal(await partner.lookupVehicleId("C1"), "veh_1");
  assert.deepEqual(sleeps, [500, 1000]);
});

test("other 5xx statuses are retried", async () => {
  const { partner, sleeps } = client([res(502), res(204)]);
  await partner.setAvailability("C1", "veh_1", true);
  assert.deepEqual(sleeps, [500]);
});

test("an unexpected 4xx is not retried", async () => {
  const { partner, calls } = client([res(400)]);
  await assert.rejects(partner.setAvailability("C1", "veh_1", true), (err) => !(err instanceof RetriesExhaustedError));
  assert.equal(calls.length, 1);
});

test("retryAfterMs parses seconds and HTTP dates", () => {
  const now = Date.parse("2026-09-24T17:00:00Z");
  assert.equal(retryAfterMs("3", now), 3000);
  assert.equal(retryAfterMs("0", now), 0);
  assert.equal(retryAfterMs("Thu, 24 Sep 2026 17:00:05 GMT", now), 5000);
  assert.equal(retryAfterMs("Thu, 24 Sep 2026 16:59:00 GMT", now), 0);
  assert.equal(retryAfterMs("soon", now), undefined);
  assert.equal(retryAfterMs(null, now), undefined);
});
