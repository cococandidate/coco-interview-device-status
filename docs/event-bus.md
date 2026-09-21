# Event Bus

`http://localhost:4003`

A queue with an HTTP front door. Available to you if you want it. Nothing
requires you to use it.

## Publishing

```
POST /v1/topics/{topic}/messages
Content-Type: application/json

<any JSON body>

202 Accepted
{ "messageId": "msg_000001", "topic": "device.status" }
```

## Delivery

The bus delivers by calling back into your service:

```
POST http://localhost:3000/internal/events
Content-Type: application/json
X-Message-Id: msg_000001

{
  "messageId": "msg_000001",
  "topic": "device.status",
  "attempt": 1,
  "publishedAt": "2026-09-18T17:02:11.412Z",
  "payload": <the body you published>
}
```

The subscription is already registered. `GET /v1/subscription` shows where it
points.

## Delivery semantics

- **At least once.** A message can arrive more than once.
- **No ordering.** Deliveries run concurrently, so two messages can arrive in
  either order, and a message being retried can arrive after a newer one.
- A non-2xx response or a timeout is a failed delivery. The bus retries up to
  **6 attempts**, backing off 1s, 2s, 4s, 8s, 16s. A subscriber that is down has
  about half a minute to come back.
- After the last attempt the message is **dead-lettered** and not delivered again.

`GET /v1/subscription` reports the current values.

## `GET /v1/deliveries`

Every delivery attempt the bus has made, with the status you returned.

## `GET /v1/dead-letter`

Messages that exhausted their attempts.

## `POST /v1/_debug/reset`

Clears delivery history and the dead-letter queue.

## Note

The bus reaches your service at `localhost:3000` on the shared container network,
which is where `make run` starts it.
