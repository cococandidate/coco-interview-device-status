# device-status integration

Start with [TASK.md](TASK.md).

## Layout

```
TASK.md                        what to build
docs/fleet-api.md              device state and the availability rule
docs/partner-supply-api.md     the partner platform
docs/event-bus.md              a queue with an HTTP front door
scaffolds/                     optional starting points, one per language
```

## Quick start

```
make up
```

That starts the three services you depend on. They should already be running on
the machine you are sitting at.

Your service listens on **port 3000**. Write it in whatever language you like,
starting from a scaffold in `scaffolds/` or from scratch.

```
make shell     # a terminal with Node, Go, Python, .NET and the JDK installed
make verify    # the partner's conformance suite, run against your service
make state     # what the partner and the bus have actually seen
make reset     # clear partner and bus state between runs
make logs      # logs from the three services
```

`make shell` drops you into a container that shares the network with the three
services, so `localhost:4001` and friends work from inside it exactly as the docs
describe. Your code lives in this directory on the host, so your editor works
normally.

If you would rather run your service directly on the machine instead of in that
container, stop the dev container first (`docker compose stop dev`) so port 3000
is free.

## Notes

The three services are black boxes. Their source is not in this repo, and the
docs are everything we know about them. They behave the same way every time.

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
