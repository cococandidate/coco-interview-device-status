import { createServer, type IncomingMessage, type ServerResponse } from "node:http";

export const config = {
  port: Number(process.env.PORT ?? 3000),
  fleetUrl: process.env.FLEET_URL ?? "http://localhost:4001",
  partnerUrl: process.env.PARTNER_URL ?? "http://localhost:4002",
  busUrl: process.env.BUS_URL ?? "http://localhost:4003",
};

export interface StatusChange {
  status: "ONLINE" | "OFFLINE";
  limitingFactors: string[];
  observedAt: string;
}

const STATUS_CHANGE = /^\/v1\/devices\/([^/]+)\/status$/;

async function handleStatusChange(
  req: IncomingMessage,
  res: ServerResponse,
  serial: string,
): Promise<void> {
  const changeId = req.headers["x-change-id"];
  const change = await readJson<StatusChange>(req);

  console.log(
    `change=${changeId} serial=${serial} status=${change?.status} factors=${change?.limitingFactors}`,
  );

  // TODO: the three steps in the README go here.

  sendJson(res, 200, { status: "ok" });
}

export const server = createServer(async (req, res) => {
  try {
    const path = (req.url ?? "").split("?")[0];

    const match = req.method === "POST" ? path.match(STATUS_CHANGE) : null;
    if (match) return await handleStatusChange(req, res, match[1]);

    if (req.method === "GET" && path === "/health") {
      return sendJson(res, 200, { status: "ok" });
    }

    sendJson(res, 404, { error: "not found", method: req.method, path });
  } catch (err) {
    console.error(err);
    sendJson(res, 500, { error: String(err) });
  }
});

// Guarded so a test that imports this file does not bind the port.
if (import.meta.main) {
  server.listen(config.port, () => {
    console.log(`listening on :${config.port}`);
  });
}

// --- plumbing, nothing below here is part of the exercise ---

export interface HttpResponse<T = unknown> {
  status: number;
  body: T | undefined;
}

export async function get<T = unknown>(url: string): Promise<HttpResponse<T>> {
  return await call<T>("GET", url);
}

export async function put<T = unknown>(url: string, body: unknown): Promise<HttpResponse<T>> {
  return await call<T>("PUT", url, body);
}

export async function post<T = unknown>(url: string, body: unknown): Promise<HttpResponse<T>> {
  return await call<T>("POST", url, body);
}

const REQUEST_TIMEOUT_MS = 5000;

async function call<T>(method: string, url: string, body?: unknown): Promise<HttpResponse<T>> {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? {} : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  });

  const text = await res.text();
  return { status: res.status, body: text ? (JSON.parse(text) as T) : undefined };
}

export async function readJson<T>(req: IncomingMessage): Promise<T | undefined> {
  const chunks: Buffer[] = [];
  for await (const chunk of req) chunks.push(chunk as Buffer);
  if (chunks.length === 0) return undefined;
  return JSON.parse(Buffer.concat(chunks).toString()) as T;
}

export function sendJson(res: ServerResponse, code: number, body: unknown): void {
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}
