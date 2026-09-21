# device-status integration

You are building the Integrations service, which sits between our robot fleet and
a delivery partner's platform. The Fleet service has just started emitting device
status changes and nothing consumes them yet. Build the endpoint that receives
them and keep the partner's view of each robot up to date through their API.

The partner's dispatch system reads it continuously and acts on whatever it last
saw. There is no reconciliation job and no polling fallback, so what you write is
what it knows.

- Available when it is not: the partner sends an order nobody can pick up, and
  the customer waits it out.
- Unavailable when it is not: the robot sits idle and we lose the work.

Seconds of staleness are fine. Minutes are not.

## Setup

```
make up                # the three services you depend on
make run typescript    # or go, python, csharp, java
```

Your code is `service/<language>/`, and you edit it in place. It includes a handler to handle
the Fleet service's request, parses the request, and has helpers for calling the other
services over HTTP.


## Incoming Fleet request

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

The Fleet service starts sending changes as soon as your service is listening.

- It times out at **500ms** and ignores your response body. Any 2xx means delivered.
- On a 5xx or a timeout it retries twice, then **drops the update**.
- One call per state change.

## Partner integration

For every status change, the partner's record for that robot ends up matching
whether the robot can currently take work. That means:

1. Decide whether the robot is available. `docs/fleet-api.md` has the rule.
2. Map our serial to the partner's vehicle id. `docs/partner-supply-api.md`.
3. Write the availability to the partner. Same doc.

## Optional: Event Bus

`docs/event-bus.md` describes a queue that is available to you, should you want
to queue up tasks.

## Commands

```
make run go       # start your service. also typescript, python, csharp, java
make test go      # run your tests
make verify       # the partner's conformance suite, run against your service
make state        # what the fleet sent, and what the partner and bus have seen
make reset        # clear partner and bus state between runs
make traffic-off  # stop the fleet emitting, for a quiet read. traffic-on resumes
make logs         # logs from the three services
make shell        # a terminal in the container, for a package install
```

`make verify` is the suite the partner runs against integrations before a
release. It is a release gate, not a specification.

Everything runs in a container that shares a network with the three services, so
`localhost:4001` and friends work exactly as the docs describe. Your code lives
in this directory on the host, so your editor works normally.

## When you are done

Push a branch and open a pull request. Write the description yourself, there is
no template. Your interviewer will read it the way they would read a real PR from
a teammate, before they read the diff.

## Scope

Most people do not finish everything they would want to, and that is fine. How
you work and what you choose to do first matter more than a complete feature.

Use whatever tools you normally use, AI included. We will ask you about any part
of what you produce.

---

## Setting up a machine

Once per machine, before an interview:

```
echo $GITHUB_TOKEN | docker login ghcr.io -u <user> --password-stdin
make pull
make up
curl localhost:4001/v1/health
curl localhost:4002/v1/health
curl localhost:4003/v1/health
```

The token needs `read:packages`. Both images are multi-arch, so Apple Silicon and
x86 machines pull the same tags.
