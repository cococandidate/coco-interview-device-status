# device-status integration

## The problem

Coco's delivery robots take orders from DeliverMe, a food-delivery marketplace.
DeliverMe's dispatcher only offers an order to a robot it believes is free, and
it believes whatever we last told it. There is no reconciliation job and no
polling fallback on their side, so what we write is what they know.

Today nothing tells them. Our Fleet service has just started publishing a message
every time a robot changes state (goes offline, runs low on battery, gets pulled
for maintenance), and nothing consumes those messages yet.

You are building that consumer: the piece of our Integrations service that keeps
DeliverMe's view of each robot matching whether it can actually take work.

Getting it wrong costs money in both directions:

- **Shown available when it is not.** DeliverMe sends an order to a robot that
  cannot go. The customer waits, the restaurant's food goes cold, and we eat the
  refund and the merchant's trust.
- **Shown unavailable when it is not.** The robot sits on the curb while
  DeliverMe hands the order to a human courier. We lose the revenue, and our
  utilization numbers look worse than the fleet really is.

Seconds of staleness are fine. Minutes are not.

## What to build

For every status change on the `device-status` queue:

1. **Decide whether the robot is available**, using the rule under
   [Fleet](#fleet-localhost4001).
2. **Find DeliverMe's vehicle id** for our serial.
3. **Write the availability** to DeliverMe.

```
make up
make run go       # or typescript, python, csharp, java
```

Each scaffold already connects, consumes `device-status`, parses the message and
acks it. The handler body is where your work goes. Starting from scratch in
another language is fine too, as long as it consumes that queue.

## When you are done

Push a branch and open a pull request. Write the description yourself, there is
no template. Your interviewer will read it the way they would read a real PR from
a teammate, before they read the diff. Leave yourself ten minutes for it.

Most people do not finish everything they would want to, and that is fine. How
you work and what you choose to do first matter more than a complete feature.
Use whatever tools you normally use, AI included. We will ask you about any part
of what you produce.

---

## The device-status queue

RabbitMQ on `localhost:5672`, management UI on
[localhost:15672](http://localhost:15672) with `guest` / `guest`. The exchange,
queue and dead letter path already exist, so you only have to consume.

```
exchange     fleet                  (topic)
routing key  device.status.changed
queue        device-status          (durable, dead letters to fleet.dlx)
dead letter  device-status.dlq
```

A message:

```
message_id:   chg_4f1a9c02be71
content_type: application/json

{
  "serial": "C10393",
  "status": "ONLINE",
  "limitingFactors": ["LOW_BATTERY"],
  "observedAt": "2026-09-24T17:02:11.482Z"
}
```

`status` is `ONLINE` or `OFFLINE`. `limitingFactors` is a possibly empty list of
reasons the robot cannot take work. `message_id` identifies the state change;
`observedAt` is when the fleet saw it.

Delivery semantics, worth reading before you design around them:

- **At least once.** The fleet republishes a change it is not certain landed, so
  the same `message_id` can arrive more than once. A republished message is a new
  delivery: `redelivered` is false on it, exactly as on the first one.
- **No ordering.** Nothing guarantees that two changes for the same robot arrive,
  or are handled, in the order the fleet observed them.
- **Ack or it comes back.** An unacked message is redelivered when your consumer
  disconnects. A `basic.nack` with requeue false sends it to `device-status.dlq`,
  where nothing reads it.
- The fleet publishes continuously while the stack is up, across several robots.

## Fleet, `localhost:4001`

Owns device state for the fleet.

**Availability rule.** A robot is available to take work when its status is
`ONLINE` and none of its limiting factors block it. These block:

| Factor | Meaning |
|---|---|
| `PILOT_REVIEW` | Held for remote pilot review |
| `LOW_BATTERY` | Below the dispatch threshold |
| `HARDWARE_FAULT` | A component reported a critical fault |
| `MAINTENANCE` | Pulled from service by a field operator |
| `OUT_OF_ZONE` | Outside its hub's operating area |

Any other limiting factor is informational and does not block.

| Endpoint | Returns |
|---|---|
| `GET /v1/devices/{serial}` | The fleet's current record for a robot: `serial`, `status`, `limitingFactors`, `observedAt`, `hub`. 404 if unknown. |
| `GET /v1/devices` | Every seeded robot. Useful for finding serials to test with. |
| `GET /v1/health` | |

## DeliverMe Supply API, `localhost:4002`

DeliverMe's platform. We do not own it and we cannot change it.

- A write typically takes **600 to 800ms**.
- It returns **503** intermittently under load.
- Vehicle ids are DeliverMe's, not our serials.

**`GET /v1/vehicles?serial={serial}`** maps one of our serials to DeliverMe's
vehicle id. 404 if the robot is not registered with DeliverMe.

```
200 OK
{ "serial": "C10393", "vehicleId": "veh_8f21c4" }
```

**`PUT /v1/vehicles/{vehicleId}/availability`** sets whether DeliverMe may send
work to that vehicle. 404 if the vehicle id is unknown, 503 when DeliverMe is
shedding load.

```
PUT /v1/vehicles/veh_8f21c4/availability
{ "available": true }

204 No Content
```

**`GET /v1/vehicles/{vehicleId}/availability`** is what DeliverMe currently
believes. 404 before anything has been written.

## Commands

```
make run go       # start your consumer. also typescript, python, csharp, java
make test go      # run your tests
make verify       # DeliverMe's conformance suite
make state        # queue depth, what the fleet published, what DeliverMe received
make reset        # clear DeliverMe's state and purge the queues
make traffic-off  # stop the fleet publishing, for a quiet read. traffic-on resumes
make logs         # logs from the fleet and DeliverMe services
make shell        # a terminal in the container, for a package install
```

`make verify` is the suite DeliverMe runs against us before a release. It is a
release gate, not a specification.

Everything runs in a container that shares a network with the broker and the two
services, so the `localhost` addresses above work exactly as written. Your code
lives in this directory on the host, so your editor works normally. The fleet and
DeliverMe services are black boxes: their source is not in this repo, this page
is everything we know about them, and they behave the same way every time.

## Scaffolds

One per language, with the AMQP client already installed in the container.

| Language | Start | Runs as | `make test <lang>` runs |
|---|---|---|---|
| TypeScript / Node | `make run typescript` | `node server.ts` | `node --test` |
| Go | `make run go` | `go run .` | `go test ./...` |
| Python | `make run python` | `python3 server.py` | `pytest` |
| C# | `make run csharp` | `dotnet run` | `dotnet test tests` |
| Java | `make run java` | `javac && java Server` | JUnit 5 |

The TypeScript one is real TypeScript. Node runs it directly and `tsc --noEmit`
is on the path.

For C#, `make test csharp` expects a project in `service/csharp/tests`. Create it
once from `make shell`:

```
cd service/csharp
dotnet new xunit -o tests && dotnet add tests reference candidate.csproj
```
