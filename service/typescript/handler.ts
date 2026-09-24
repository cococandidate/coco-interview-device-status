import { isAvailable } from "./availability.ts";
import { NotFoundError, RetriesExhaustedError, type PartnerClient } from "./partner.ts";
import type { StatusTracker } from "./tracker.ts";

export interface StatusChange {
  serial: string;
  status: "ONLINE" | "OFFLINE";
  limitingFactors: string[];
  observedAt: string;
}

// What to do with the delivery. "nack" dead letters it, "requeue" puts it back
// on the queue.
export type Outcome = "ack" | "nack" | "requeue";

export interface Deps {
  tracker: StatusTracker;
  partner: Pick<PartnerClient, "lookupVehicleId" | "setAvailability">;
}

export async function handleStatusChange(messageId: string | undefined, content: string, deps: Deps): Promise<Outcome> {
  const change = parse(content);
  const observedAtMs = change ? Date.parse(change.observedAt) : NaN;
  if (!messageId || !change || Number.isNaN(observedAtMs)) {
    console.error(`change=${messageId} malformed, dropping: ${content}`);
    return "nack";
  }

  const tag = `change=${messageId} serial=${change.serial}`;

  const verdict = deps.tracker.check(messageId, change.serial, observedAtMs);
  if (verdict === "duplicate") {
    console.log(`${tag} already applied, skipping`);
    return "ack";
  }
  if (verdict === "stale") {
    const latest = new Date(deps.tracker.latestFor(change.serial)!).toISOString();
    console.log(`${tag} observedAt=${change.observedAt} is not newer than applied ${latest}, skipping`);
    return "ack";
  }

  const available = isAvailable(change);

  try {
    const vehicleId = await deps.partner.lookupVehicleId(change.serial);
    await deps.partner.setAvailability(change.serial, vehicleId, available);
    deps.tracker.record(messageId, change.serial, observedAtMs);
    console.log(`${tag} vehicle=${vehicleId} available=${available} status=${change.status} factors=${change.limitingFactors}`);
    return "ack";
  } catch (err) {
    if (err instanceof NotFoundError) {
      console.error(`${tag} ${err.message}, dropping`);
      return "nack";
    }
    if (err instanceof RetriesExhaustedError) {
      console.error(`${tag} ${err.message}, requeueing`);
      return "requeue";
    }
    throw err;
  }
}

function parse(content: string): StatusChange | undefined {
  let value: unknown;
  try {
    value = JSON.parse(content);
  } catch {
    return undefined;
  }
  const c = value as Partial<StatusChange> | null;
  if (
    typeof c?.serial !== "string" ||
    (c.status !== "ONLINE" && c.status !== "OFFLINE") ||
    !Array.isArray(c.limitingFactors) ||
    !c.limitingFactors.every((f) => typeof f === "string") ||
    typeof c.observedAt !== "string"
  ) {
    return undefined;
  }
  return c as StatusChange;
}
