export type Verdict = "new" | "duplicate" | "stale";

// In-memory pre-checks. Both maps grow without bound and are lost on restart,
// which is acceptable for now.
export class StatusTracker {
  private readonly seenIds = new Set<string>();
  private readonly latestBySerial = new Map<string, number>();

  check(messageId: string, serial: string, observedAtMs: number): Verdict {
    if (this.seenIds.has(messageId)) return "duplicate";
    const latest = this.latestBySerial.get(serial);
    // observedAt has one second resolution, so an equal timestamp is treated
    // as not newer than what the partner already has.
    if (latest !== undefined && observedAtMs <= latest) return "stale";
    return "new";
  }

  // Called only once the partner has accepted the write, so a change that
  // failed can still be applied when the fleet republishes it.
  record(messageId: string, serial: string, observedAtMs: number): void {
    this.seenIds.add(messageId);
    const latest = this.latestBySerial.get(serial);
    if (latest === undefined || observedAtMs > latest) this.latestBySerial.set(serial, observedAtMs);
  }

  latestFor(serial: string): number | undefined {
    return this.latestBySerial.get(serial);
  }
}
