import amqp, { type Channel } from "amqplib";
import { handleStatusChange } from "./handler.ts";
import { get, put } from "./http.ts";
import { PartnerClient } from "./partner.ts";
import { StatusTracker } from "./tracker.ts";

export const config = {
  amqpUrl: process.env.AMQP_URL ?? "amqp://guest:guest@localhost:5672/",
  queue: process.env.QUEUE ?? "device-status",
  fleetUrl: process.env.FLEET_URL ?? "http://localhost:4001",
  partnerUrl: process.env.PARTNER_URL ?? "http://localhost:4002",
};

async function main(): Promise<void> {
  const channel = await connect();
  await channel.prefetch(1);

  const deps = {
    tracker: new StatusTracker(),
    partner: new PartnerClient(config.partnerUrl, { get, put }),
  };

  console.log(`consuming ${config.queue}`);

  await channel.consume(config.queue, async (msg) => {
    if (msg === null) return;
    try {
      const outcome = await handleStatusChange(msg.properties.messageId, msg.content.toString(), deps);
      if (outcome === "ack") channel.ack(msg);
      else channel.nack(msg, false, outcome === "requeue");
    } catch (err) {
      console.error(`message ${msg.properties.messageId} failed:`, err);
      channel.nack(msg, false, false);
    }
  });
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

// --- plumbing, nothing below here is part of the exercise ---

async function connect(): Promise<Channel> {
  for (let attempt = 1; ; attempt++) {
    try {
      const conn = await amqp.connect(config.amqpUrl);
      return await conn.createChannel();
    } catch (err) {
      if (attempt >= 30) throw err;
      await new Promise((r) => setTimeout(r, 1000));
    }
  }
}
