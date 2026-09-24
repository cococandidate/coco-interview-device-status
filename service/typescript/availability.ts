import type { StatusChange } from "./handler.ts";

// From docs/fleet-api.md. Any other limiting factor is informational.
export const BLOCKING_FACTORS: ReadonlySet<string> = new Set([
  "PILOT_REVIEW",
  "LOW_BATTERY",
  "HARDWARE_FAULT",
  "MAINTENANCE",
  "OUT_OF_ZONE",
]);

export function isAvailable(change: Pick<StatusChange, "status" | "limitingFactors">): boolean {
  return change.status === "ONLINE" && !change.limitingFactors.some((f) => BLOCKING_FACTORS.has(f));
}
