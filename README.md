# device-status integration

Start with [TASK.md](TASK.md).

## Layout

```
TASK.md                        what to build
service/                       your code, once you run make start-<language>
docs/fleet-api.md              device state and the availability rule
docs/partner-supply-api.md     the partner platform
docs/event-bus.md              a queue with an HTTP front door
scaffolds/                     starting points, one per language
```

## Quick start

```
make up                 # the three services, probably already running
make start-typescript   # or start-go, start-python, start-csharp, start-java
make run
```

`make start-<language>` copies that scaffold into `service/`. It serves
`POST /v1/devices/{serial}/status`, parses the request, and has HTTP helpers for
calling the other services. The handler body is yours.

```
make verify    # the partner's conformance suite, run against your service
make state     # what the partner and the bus have actually seen
make reset     # clear partner and bus state between runs
make shell     # a terminal in that container, for a package install or a test run
make logs      # logs from the three services
```

`make run` and `make shell` both drop into a container that shares the network
with the three services, so `localhost:4001` and friends work from inside exactly
as the docs describe. Port 3000 is published from that container to the host, so
your service has to run there rather than directly on the machine. Your code
lives in this directory on the host, so your editor works normally.

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
