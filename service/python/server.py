import json
import os
import sys
import time
import urllib.error
import urllib.request

import pika

CONFIG = {
    "amqp_url": os.environ.get("AMQP_URL", "amqp://guest:guest@localhost:5672/"),
    "queue": os.environ.get("QUEUE", "device-status"),
    "fleet_url": os.environ.get("FLEET_URL", "http://localhost:4001"),
    "partner_url": os.environ.get("PARTNER_URL", "http://localhost:4002"),
}


def handle_status_change(channel, method, properties, body):
    change = json.loads(body)

    print(
        f"change={properties.message_id} serial={change['serial']} "
        f"status={change['status']} factors={change['limitingFactors']} "
        f"redelivered={method.redelivered}"
    )

    # TODO: the steps in the README go here.

    channel.basic_ack(method.delivery_tag)


def main():
    channel = connect()
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(CONFIG["queue"], handle_status_change)
    print(f"consuming {CONFIG['queue']}")
    channel.start_consuming()


# --- plumbing, nothing below here is part of the exercise ---


def connect():
    for attempt in range(1, 31):
        try:
            conn = pika.BlockingConnection(pika.URLParameters(CONFIG["amqp_url"]))
            return conn.channel()
        except Exception:
            if attempt >= 30:
                raise
            time.sleep(1)


DEFAULT_TIMEOUT_MS = 3000


def get(url, timeout_ms=DEFAULT_TIMEOUT_MS):
    return call("GET", url, timeout_ms=timeout_ms)


def put(url, body=None, timeout_ms=DEFAULT_TIMEOUT_MS):
    return call("PUT", url, body, timeout_ms=timeout_ms)


def post(url, body=None, timeout_ms=DEFAULT_TIMEOUT_MS):
    return call("POST", url, body, timeout_ms=timeout_ms)


def call(method, url, body=None, timeout_ms=DEFAULT_TIMEOUT_MS):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")

    try:
        with urllib.request.urlopen(req, timeout=timeout_ms / 1000) as res:
            raw = res.read()
            return {"status": res.status, "body": json.loads(raw) if raw else None}
    except urllib.error.HTTPError as err:
        raw = err.read()
        return {"status": err.code, "body": json.loads(raw) if raw else None}


if __name__ == "__main__":
    sys.stdout.reconfigure(line_buffering=True)
    main()
