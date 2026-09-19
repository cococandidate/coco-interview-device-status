# Task

You are on the integrations team, between our robot fleet and a delivery
partner's platform. The fleet service has just started emitting device status
changes and nothing consumes them yet. Build the endpoint that receives them and
keeps the partner's view of each robot correct.

## Build this

```
POST /v1/devices/{serial}/status
```

Your service listens on **port 3000**.

```
make start-go     # or start-typescript, start-python, start-csharp, start-java
make run
```

`make start-<language>` copies a scaffold into `service/`. It already serves the
route above, parses the request, and has helpers for calling the other services
over HTTP. The handler body is where your work goes. Starting from scratch in
another language is fine too, as long as it listens on port 3000.

### The request

```
POST /v1/devices/C10393/status
Content-Type: application/json
X-Change-Id: chg_01J8ZQ4M7XN2VB

{
  "status": "ONLINE",
  "limitingFactors": ["LOW_BATTERY"],
  "observedAt": "2026-09-18T17:02:11Z"
}
```

`status` is `ONLINE` or `OFFLINE`. `limitingFactors` is a possibly empty list of
reasons the robot cannot take work. `X-Change-Id` is unique per state change.

### What it has to do

For every status change, the partner's record for that robot ends up matching
whether the robot can currently take work. That means:

1. Decide whether the robot is available. `docs/fleet-api.md` has the rule.
2. Map our serial to the partner's vehicle id. `docs/partner-supply-api.md`.
3. Write the availability to the partner. Same doc.

A recent fleet release added `MAINTENANCE` to the limiting factors it can emit,
so you will see it in traffic.

`docs/event-bus.md` describes a queue that is available to you. Nothing requires
it.

### How the fleet service calls you

- It times out at **500ms** and ignores your response body. Any 2xx means delivered.
- On a 5xx or a timeout it retries twice, then **drops the update**.
- One call per state change, across a few hundred robots.

### Why the record has to be right

The partner's dispatch system reads it continuously and acts on whatever it last
saw. There is no reconciliation job and no polling fallback, so what you write is
what it knows.

- Available when it is not: the partner sends an order nobody can pick up, and
  the customer waits it out.
- Unavailable when it is not: the robot sits idle and we lose the work.

Seconds of staleness are fine. Minutes are not.

## Running things

```
make up        # the three services you depend on
make run       # start your service from service/
make verify    # the partner's conformance suite, run against your service
make state     # what the partner and the bus have actually seen
make reset     # clear partner and bus state between runs
make shell     # a terminal with every toolchain installed
```

Your service runs in a container that shares a network with the three services,
which is what `make run` and `make shell` give you. `localhost:4001` and friends
work there exactly as the docs describe. Your code lives in this directory on the
host, so your editor works normally.

`make verify` is the suite the partner runs against integrations before a
release. It is a release gate, not a specification.

## When you are done

Push a branch and open a pull request. Write the description yourself, there is
no template. Your interviewer will read it the way they would read a real PR from
a teammate, before they read the diff.

Leave yourself ten minutes for it.

## Scope

Most people do not finish everything they would want to, and that is fine. How
you work and what you choose to do first matter more than a complete feature.

Use whatever tools you normally use, AI included. We will ask you about any part
of what you produce.
