import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

CONFIG = {
    "port": int(os.environ.get("PORT", "3000")),
    "fleet_url": os.environ.get("FLEET_URL", "http://localhost:4001"),
    "partner_url": os.environ.get("PARTNER_URL", "http://localhost:4002"),
    "bus_url": os.environ.get("BUS_URL", "http://localhost:4003"),
}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/health":
            return self.send_json(200, {"status": "ok"})
        self.send_json(404, {"error": "not found", "path": self.path})

    def do_POST(self):
        self.send_json(404, {"error": "not found", "path": self.path})

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
        print(f"{self.command} {self.path}")


if __name__ == "__main__":
    print(f"listening on :{CONFIG['port']}")
    ThreadingHTTPServer(("", CONFIG["port"]), Handler).serve_forever()
