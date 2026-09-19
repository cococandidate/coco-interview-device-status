import { createServer } from "node:http";

export const config = {
  port: Number(process.env.PORT ?? 3000),
  fleetUrl: process.env.FLEET_URL ?? "http://localhost:4001",
  partnerUrl: process.env.PARTNER_URL ?? "http://localhost:4002",
  busUrl: process.env.BUS_URL ?? "http://localhost:4003",
};

async function readJson(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  if (chunks.length === 0) return undefined;
  return JSON.parse(Buffer.concat(chunks).toString());
}

const server = createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok" }));
    return;
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ error: "not found", method: req.method, path: req.url }));
});

server.listen(config.port, () => {
  console.log(`listening on :${config.port}`);
});

export { readJson };
