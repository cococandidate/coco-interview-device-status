import type { HttpResponse } from "./http.ts";

export interface Http {
  get(url: string): Promise<HttpResponse>;
  put(url: string, body: unknown): Promise<HttpResponse>;
}

export interface RetryPolicy {
  maxAttempts: number;
  baseDelayMs: number;
  // Total time we are willing to sleep across retries for one request.
  maxTotalDelayMs: number;
}

export const DEFAULT_RETRY: RetryPolicy = { maxAttempts: 5, baseDelayMs: 500, maxTotalDelayMs: 10_000 };

// The partner does not know this serial or vehicle id. Retrying will not help.
export class NotFoundError extends Error {}

// Every attempt hit a retryable failure (5xx, timeout, network error).
export class RetriesExhaustedError extends Error {}

export class PartnerClient {
  private readonly baseUrl: string;
  private readonly http: Http;
  private readonly retry: RetryPolicy;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly vehicleIds = new Map<string, string>();

  constructor(
    baseUrl: string,
    http: Http,
    retry: RetryPolicy = DEFAULT_RETRY,
    sleep: (ms: number) => Promise<void> = (ms) => new Promise((r) => setTimeout(r, ms)),
  ) {
    this.baseUrl = baseUrl;
    this.http = http;
    this.retry = retry;
    this.sleep = sleep;
  }

  async lookupVehicleId(serial: string): Promise<string> {
    const cached = this.vehicleIds.get(serial);
    if (cached !== undefined) return cached;

    const url = `${this.baseUrl}/v1/vehicles?serial=${encodeURIComponent(serial)}`;
    const res = await this.withRetry(`GET vehicle for ${serial}`, () => this.http.get(url));

    if (res.status === 404) throw new NotFoundError(`serial ${serial} is not registered with the partner`);
    const vehicleId = (res.body as { vehicleId?: unknown } | undefined)?.vehicleId;
    if (res.status !== 200 || typeof vehicleId !== "string") {
      throw new Error(`unexpected vehicle lookup response for ${serial}: status ${res.status}`);
    }

    this.vehicleIds.set(serial, vehicleId);
    return vehicleId;
  }

  async setAvailability(serial: string, vehicleId: string, available: boolean): Promise<void> {
    const url = `${this.baseUrl}/v1/vehicles/${encodeURIComponent(vehicleId)}/availability`;
    const res = await this.withRetry(`PUT availability for ${vehicleId}`, () => this.http.put(url, { available }));

    if (res.status === 204) return;
    if (res.status === 404) {
      // Our cached mapping is probably stale. Drop it so the next change for
      // this serial looks the vehicle up again.
      this.vehicleIds.delete(serial);
      throw new NotFoundError(`vehicle ${vehicleId} (serial ${serial}) is unknown to the partner`);
    }
    throw new Error(`unexpected availability response for ${vehicleId}: status ${res.status}`);
  }

  private async withRetry(label: string, request: () => Promise<HttpResponse>): Promise<HttpResponse> {
    let sleptMs = 0;
    for (let attempt = 1; ; attempt++) {
      let res: HttpResponse | undefined;
      let reason: string;
      try {
        res = await request();
        if (res.status < 500) return res;
        reason = `status ${res.status}`;
      } catch (err) {
        // The http helpers only throw on transport failures: timeouts,
        // refused connections, resets.
        reason = err instanceof Error ? `${err.name}: ${err.message}` : String(err);
      }

      const delayMs = retryAfterMs(res?.headers.get("retry-after") ?? null) ?? this.retry.baseDelayMs * 2 ** (attempt - 1);
      if (attempt >= this.retry.maxAttempts || sleptMs + delayMs > this.retry.maxTotalDelayMs) {
        throw new RetriesExhaustedError(`${label} gave up after ${attempt} attempts, last failure ${reason}`);
      }

      console.warn(`${label} attempt ${attempt} failed (${reason}), retrying in ${delayMs}ms`);
      await this.sleep(delayMs);
      sleptMs += delayMs;
    }
  }
}

// Retry-After is either delay-seconds or an HTTP date.
export function retryAfterMs(header: string | null, now: number = Date.now()): number | undefined {
  if (header === null || header.trim() === "") return undefined;
  const seconds = Number(header);
  if (Number.isFinite(seconds) && seconds >= 0) return seconds * 1000;
  const date = Date.parse(header);
  if (Number.isNaN(date)) return undefined;
  return Math.max(0, date - now);
}
