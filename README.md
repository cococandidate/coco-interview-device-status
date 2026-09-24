# device-status integration

You are building the Integrations service, which sits between our robot fleet and
a delivery partner's platform. The Fleet service has just started publishing
device status changes and nothing consumes them yet. Build the consumer and keep
the partner's view of each robot up to date through their API.

The Fleet service publishes to the `device-status` queue on RabbitMQ. The
exchange, queue and dead letter path already exist.

```
make up
make run go       # or typescript, python, csharp, java
```

Each scaffold already connects, consumes `device-status`, parses the message and
acks it. The handler body is where your work goes. Starting from scratch in
another language is fine too, as long as it consumes that queue.

## Partner integration

For every status change, the partner's record for that robot ends up matching
whether the robot can currently take work. That means:

1. Decide whether the robot is available. `docs/fleet-api.md` has the rule.
2. Map our serial to the partner's vehicle id. `docs/partner-supply-api.md`.
3. Write the availability to the partner. Same doc.

`docs/device-status-queue.md` has the message shape and the delivery semantics.
They are worth reading before you design around them.

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
make run go       # start your consumer. also typescript, python, csharp, java
make test go      # run your tests
make verify       # the partner's conformance suite
make state        # queue depth, what the fleet published, what the partner saw
make reset        # clear partner state and purge the queues
make traffic-off  # stop the fleet publishing, for a quiet read. traffic-on resumes
make logs         # logs from the fleet and partner services
make shell        # a terminal in the container, for a package install
```

`make verify` is the suite the partner runs against integrations before a
release. It is a release gate, not a specification.

Everything runs in a container that shares a network with the broker and the two
services, so `localhost:5672` and `localhost:4001` work exactly as the docs
describe. Your code lives in this directory on the host, so your editor works
normally.

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

## Reference

```
docs/device-status-queue.md    the queue, the message and its delivery semantics
docs/fleet-api.md              device state and the availability rule
docs/partner-supply-api.md     the partner platform
```

The fleet and partner services are black boxes. Their source is not in this repo,
and the docs are everything we know about them. They behave the same way every
time.

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

Both images are public, so nothing here needs a GitHub login or a token.

Once per machine:

```
gh repo clone cocorobotics/coco-interview-device-status
cd coco-interview-device-status
make pull
```

The morning of:

```
git checkout main && git pull && git clean -fd
make up

curl localhost:4001/v1/health
curl localhost:4002/v1/health
```

Leave the stack running and the repo open in the editor before they sit down.
For TypeScript, run `make run typescript` once and stop it, so `node_modules` is
already installed.
