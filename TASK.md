# Task

You are on the integrations team. We run the seam between our robot fleet and a
delivery partner's supply platform: device state comes in from internal services,
partner-visible availability goes out.

The fleet service has just started emitting device status changes to us. Nothing
consumes them yet. Your job is to build the endpoint that receives them and keeps
the partner's view of each robot correct.

## What to build

```
POST /v1/devices/{serial}/status
```

Your service listens on **port 3000**. Language and framework are your choice.

### Request

```
POST /v1/devices/C10393/status
Content-Type: application/json
X-Change-Id: chg_01J8ZQ...

{
  "status": "ONLINE",
  "limitingFactors": ["LOW_BATTERY"],
  "observedAt": "2026-09-18T17:02:11Z"
}
```

`status` is `ONLINE` or `OFFLINE`. `limitingFactors` is a possibly empty list of
reasons the device cannot currently take work. `X-Change-Id` is unique per state
change.

### How the fleet service calls you

- It **times out at 500ms** and ignores your response body. Any 2xx means delivered.
- On a 5xx or a timeout it **retries twice**, then **drops the update**.
- It emits a change every time a device's computed status changes. In production
  that is a few hundred robots.

### What has to end up true

The partner's record for that robot reflects whether the robot can currently take
work. See `docs/partner-supply-api.md` for how to write it and
`docs/fleet-api.md` for the rule that decides it.

A recent fleet release added `MAINTENANCE` to the set of limiting factors it can
emit, so you will see it in traffic.

## Running things

```
make up        # starts the three services you depend on
make verify    # runs the partner's conformance suite against your service
make state     # dumps what the partner and the bus have actually seen
make reset     # clears partner and bus state between runs
```

`make verify` is the same suite the partner runs against integrations before a
release. It is a release gate, not a specification.

## When you are done

Push a branch and open a pull request. Write the description yourself, there is no
template. Your interviewer is going to read it the way they would read a real PR
from a teammate, before they read the diff.

## A note on scope

Most people do not finish everything they would want to. That is expected and it
is fine. How you work and what you choose to do first matter more here than
getting to a complete feature.

Use whatever tools you normally use, AI included. We will ask you about any part
of what you produce.
