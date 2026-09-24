# Device status queue

RabbitMQ on `localhost:5672`, management UI on
[localhost:15672](http://localhost:15672) with `guest` / `guest`.

The fleet publishes a message every time a device changes state. The exchange,
the queue and its dead letter path already exist, so you only have to consume.

```
exchange     fleet                  (topic)
routing key  device.status.changed
queue        device-status          (durable, dead letters to fleet.dlx)
dead letter  device-status.dlq
```

## The message

```
message_id:   chg_000412
content_type: application/json

{
  "serial": "C10393",
  "status": "ONLINE",
  "limitingFactors": ["LOW_BATTERY"],
  "observedAt": "2026-09-24T17:02:11Z"
}
```

`status` is `ONLINE` or `OFFLINE`. `limitingFactors` is a possibly empty list of
reasons the robot cannot take work. `message_id` identifies the state change.

## Delivery semantics

- **At least once.** The fleet republishes a change it is not certain landed, so
  the same `message_id` can arrive more than once. A republished message is a new
  delivery: `redelivered` is false on it, exactly as on the first one.
- **No ordering.** Nothing guarantees two changes for the same robot are handled
  in the order they were published.
- **Ack or it comes back.** An unacked message is redelivered when your consumer
  disconnects. A `basic.nack` with requeue false sends it to `device-status.dlq`,
  where nothing reads it.
- The fleet publishes continuously while the stack is up, across several robots.

## Looking at it

```
make state     # queue depth, consumer count, what the fleet published
```

The management UI shows the same thing, plus per-queue message rates.
