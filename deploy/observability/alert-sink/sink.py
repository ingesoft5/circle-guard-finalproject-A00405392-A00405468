"""Receptor de notificaciones de Alertmanager para el compose (HU-21).

Imprime una línea legible por alerta (estado, nombre, severidad y resumen) y la guarda en /data/notificaciones.log.
Sirve de evidencia y para la demo; en la nube el receptor es Discord o WhatsApp.
"""
import json
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, HTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        now = datetime.now(timezone.utc).strftime("%H:%M:%SZ")
        lines = []
        for alert in body.get("alerts", []):
            labels, notes = alert.get("labels", {}), alert.get("annotations", {})
            state = "DISPARADA" if alert.get("status") == "firing" else "RESUELTA"
            lines.append(f"[{now}] {state} {labels.get('alertname')} ({labels.get('severity')}): {notes.get('summary')}")
        with open("/data/notificaciones.log", "a", encoding="utf-8") as log:
            for line in lines:
                print(line, flush=True)
                log.write(line + "\n")
        self.send_response(200)
        self.end_headers()

    def do_GET(self):  # healthcheck
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok\n")

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
