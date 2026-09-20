# device-status integration

You are building the Integrations service, which sits between our robot fleet and
a delivery partner's platform. The Fleet service has just started emitting device
status changes and nothing consumes them yet. Build the endpoint that receives
them and keep the partner's view of each robot up to date through their API.

The Fleet service publishes to `POST /v1/devices/{serial}/status` on port 3000.

```
make up           # the three services you depend on
make start-go     # or start-typescript, start-python, start-csharp, start-java
make run
```

`make start-<language>` copies a scaffold into `service/`. It already serves the
route above, parses the request, and has helpers for calling the other services
over HTTP. The handler body is where your work goes. Starting from scratch in
another language is fine too, as long as it listens on port 3000.

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

## Partner integration

For every status change, the partner's record for that robot ends up matching
whether the robot can currently take work. That means:

1. Decide whether the robot is available. `docs/fleet-api.md` has the rule.
2. Map our serial to the partner's vehicle id. `docs/partner-supply-api.md`.
3. Write the availability to the partner. Same doc.

`docs/event-bus.md` describes a queue that is available to you, should you want
to queue up tasks.

## How the Fleet service calls the Integration service

- It times out at **500ms** and ignores your response body. Any 2xx means delivered.
- On a 5xx or a timeout it retries twice, then **drops the update**.
- One call per state change.

## Why the record has to be right

The partner's dispatch system reads it continuously and acts on whatever it last
saw. There is no reconciliation job and no polling fallback, so what you write is
what it knows.

- Available when it is not: the partner sends an order nobody can pick up, and
  the customer waits it out.
- Unavailable when it is not: the robot sits idle and we lose the work.

Seconds of staleness are fine. Minutes are not.

## Commands

```
make run          # start your service from service/
make test         # run your tests
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

## Scaffolds

Optional, one per language, standard library only so nothing needs installing.
Each serves the route and `GET /health` on port 3000, parses the body and the
change id, and has `get` / `put` / `post` helpers for the other services.

| Language | Start | Runs as | `make test` runs |
|---|---|---|---|
| TypeScript / Node | `make start-typescript` | `node server.ts` | `node --test` |
| Go | `make start-go` | `go run .` | `go test ./...` |
| Python | `make start-python` | `python3 server.py` | `pytest` |
| C# | `make start-csharp` | `dotnet run` | `dotnet test tests` |
| Java | `make start-java` | `java Server.java` | JUnit 5 |

The TypeScript one is real TypeScript. Node runs it directly, `@types/node` is
vendored so your editor works offline, and `tsc --noEmit` is on the path.

For C#, `make test` expects a project in `service/tests`, which you can create
with `dotnet new xunit -o tests` from `make shell`.

## Reference

```
docs/fleet-api.md              device state and the availability rule
docs/partner-supply-api.md     the partner platform
docs/event-bus.md              a queue with an HTTP front door
```

The three services are black boxes. Their source is not in this repo, and the
docs are everything we know about them. They behave the same way every time.

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
