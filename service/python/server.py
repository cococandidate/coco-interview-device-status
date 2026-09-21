import json
import os
import re
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CONFIG = {
    "port": int(os.environ.get("PORT", "3000")),
    "fleet_url": os.environ.get("FLEET_URL", "http://localhost:4001"),
    "partner_url": os.environ.get("PARTNER_URL", "http://localhost:4002"),
    "bus_url": os.environ.get("BUS_URL", "http://localhost:4003"),
}

STATUS_CHANGE = re.compile(r"^/v1/devices/([^/]+)/status$")


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        match = STATUS_CHANGE.match(self.path)
        if match:
            return self.handle_status_change(match.group(1))
        self.send_json(404, {"error": "not found", "path": self.path})

    def do_GET(self):
        if self.path == "/health":
            return self.send_json(200, {"status": "ok"})
        self.send_json(404, {"error": "not found", "path": self.path})

    def handle_status_change(self, serial):
        change_id = self.headers.get("X-Change-Id")
        change = self.read_json() or {}

        print(
            f"change={change_id} serial={serial} "
            f"status={change.get('status')} factors={change.get('limitingFactors')}"
        )

        # TODO: the three steps in the README go here.

        self.send_json(200, {"status": "ok"})

    # --- plumbing, nothing below here is part of the exercise ---

    def read_json(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length == 0:
            return None
        return json.loads(self.rfile.read(length))

    def send_json(self, code, body):
        payload = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt, *args):
        pass


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
    print(f"listening on :{CONFIG['port']}")
    ThreadingHTTPServer(("", CONFIG["port"]), Handler).serve_forever()
