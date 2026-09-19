import { createServer } from "node:http";

export const config = {
  port: Number(process.env.PORT ?? 3000),
  fleetUrl: process.env.FLEET_URL ?? "http://localhost:4001",
  partnerUrl: process.env.PARTNER_URL ?? "http://localhost:4002",
  busUrl: process.env.BUS_URL ?? "http://localhost:4003",
};

const STATUS_CHANGE = /^\/v1\/devices\/([^/]+)\/status$/;

async function handleStatusChange(req, res, serial) {
  const changeId = req.headers["x-change-id"];
  const change = await readJson(req);

  console.log(
    `change=${changeId} serial=${serial} status=${change?.status} factors=${change?.limitingFactors}`,
  );

  // TODO: the three steps in TASK.md go here.

  sendJson(res, 200, { status: "ok" });
}

const server = createServer(async (req, res) => {
  try {
    const path = req.url.split("?")[0];

    const match = req.method === "POST" && path.match(STATUS_CHANGE);
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

server.listen(config.port, () => {
  console.log(`listening on :${config.port}`);
});

// --- plumbing, nothing below here is part of the exercise ---

export async function get(url) {
  return await call("GET", url);
}

export async function put(url, body) {
  return await call("PUT", url, body);
}

export async function post(url, body) {
  return await call("POST", url, body);
}

async function call(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? {} : { "Content-Type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : undefined };
}

export async function readJson(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (chunks.length === 0) return undefined;
  return JSON.parse(Buffer.concat(chunks).toString());
}

export function sendJson(res, code, body) {
  res.writeHead(code, { "Content-Type": "application/json" });
  res.end(JSON.stringify(body));
}
