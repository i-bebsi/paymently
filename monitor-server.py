#!/usr/bin/env python3
"""Monitor server for Paymently healthz dashboard.
Run: python3 monitor-server.py
Dashboard: http://localhost:9090
"""

import json
import os
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

PORT = 9090
LOG_FILE = Path(__file__).parent / "logs" / "healthz-monitor.log"
HTML_FILE = Path(__file__).parent / "dashboard.html"


class MonitorHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/api/logs":
            self._serve_json(self._read_logs())
        elif self.path == "/api/stats":
            self._serve_json(self._compute_stats())
        elif self.path == "/" or self.path == "/dashboard":
            self._serve_html()
        else:
            super().do_GET()

    def _serve_json(self, data):
        body = json.dumps(data, indent=2).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", len(body))
        self.end_headers()
        self.wfile.write(body)

    def _serve_html(self):
        if not HTML_FILE.exists():
            self.send_error(404, "dashboard.html not found")
            return
        html = HTML_FILE.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", len(html))
        self.end_headers()
        self.wfile.write(html)

    def _read_logs(self):
        if not LOG_FILE.exists():
            return []
        entries = []
        with open(LOG_FILE, "r") as f:
            for line in f:
                line = line.strip()
                if line:
                    try:
                        entries.append(json.loads(line))
                    except json.JSONDecodeError:
                        entries.append({"raw": line, "parseError": True})
        return entries

    def _compute_stats(self):
        logs = self._read_logs()
        total = len(logs)
        success = sum(1 for e in logs if e.get("status") == "success")
        failure = total - success
        failures = [e for e in logs if e.get("status") == "failure"]
        last = logs[-1] if logs else None

        return {
            "total": total,
            "success": success,
            "failure": failure,
            "lastCheck": last,
            "failures": failures[-20:],  # 20 terakhir
            "recent": logs[-10:],        # 10 terakhir (all)
        }

    def log_message(self, format, *args):
        pass  # silent HTTP logs


if __name__ == "__main__":
    os.chdir(Path(__file__).parent)
    server = HTTPServer(("0.0.0.0", PORT), MonitorHandler)
    print(f"🟢 Monitor server running at http://localhost:{PORT}")
    print(f"   Dashboard → http://localhost:{PORT}")
    print(f"   API Logs  → http://localhost:{PORT}/api/logs")
    print(f"   API Stats → http://localhost:{PORT}/api/stats")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n🔴 Server stopped.")
        server.shutdown()
