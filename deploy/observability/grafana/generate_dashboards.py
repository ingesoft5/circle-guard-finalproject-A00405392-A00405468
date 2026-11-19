"""Genera los dashboards de Grafana de CircleGuard (HU-20) como JSON.

Uso:  python deploy/observability/grafana/generate_dashboards.py
Los JSON quedan en deploy/observability/grafana/dashboards/ y Grafana los carga por provisioning.
Se generan con este script (y no a mano en la interfaz) para que las consultas queden revisables en el PR.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "dashboards"
DS = {"type": "prometheus", "uid": "prometheus"}
# Las métricas RED excluyen /actuator (los sondeos de Prometheus y Kubernetes no son tráfico de usuarios).
APP_TRAFFIC = 'application="$application", uri!~"/actuator.*"'


def panel(pid, title, exprs, x, y, w=8, h=8, unit="short", kind="timeseries", desc="", legend=None):
    targets = []
    for i, e in enumerate(exprs):
        expr, fmt = e if isinstance(e, tuple) else (e, legend)
        t = {"datasource": DS, "expr": expr, "refId": chr(65 + i)}
        if fmt:
            t["legendFormat"] = fmt
        targets.append(t)
    return {
        "id": pid, "type": kind, "title": title, "description": desc, "datasource": DS,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        # Las cifras de los "stat" no son umbrales de alarma (las alertas son HU-21): color fijo y no rojo por defecto.
        "fieldConfig": {"defaults": {"unit": unit, **({"color": {"mode": "fixed", "fixedColor": "blue"}} if kind == "stat" else {}),
                                     # increase() extrapola (p. ej. 27.3); un conteo de personas se muestra entero
                                     **({"decimals": 0} if kind == "stat" and unit == "short" else {})},
                        "overrides": []},
        "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "reduceOptions": {"calcs": ["lastNotNull"]}},
        "targets": targets,
    }


def dashboard(uid, title, panels, templating=None, desc=""):
    return {
        "uid": uid, "title": title, "description": desc, "tags": ["circleguard", "hu-20"],
        "timezone": "browser", "schemaVersion": 39, "version": 1, "refresh": "30s",
        "time": {"from": "now-1h", "to": "now"},
        "templating": {"list": templating or []}, "panels": panels,
    }


def services_dashboard():
    var = {
        "name": "application", "label": "Servicio", "type": "query", "datasource": DS,
        "query": {"query": "label_values(http_server_requests_seconds_count, application)", "refId": "app"},
        "definition": "label_values(http_server_requests_seconds_count, application)",
        "refresh": 2, "includeAll": True, "multi": True, "current": {"text": "All", "value": "$__all"},
        "sort": 1,
    }
    p = [
        # Una fila por servicio (repeat): el mismo tablero sirve de "dashboard por servicio".
        {"id": 1, "type": "row", "title": "$application", "repeat": "application", "collapsed": False,
         "gridPos": {"x": 0, "y": 0, "w": 24, "h": 1}, "panels": []},
        panel(2, "Rate: peticiones por segundo",
              [(f'sum by (uri) (rate(http_server_requests_seconds_count{{{APP_TRAFFIC}}}[5m]))', "{{uri}}")],
              0, 1, unit="reqps", desc="R de RED: peticiones atendidas por ruta (sin /actuator)."),
        panel(3, "Errors: % de respuestas 5xx",
              [(f'100 * (sum(rate(http_server_requests_seconds_count{{{APP_TRAFFIC}, status=~"5.."}}[5m])) or vector(0))'
                f' / clamp_min(sum(rate(http_server_requests_seconds_count{{{APP_TRAFFIC}}}[5m])), 1e-9)', "5xx")],
              8, 1, unit="percent", desc="E de RED: porcentaje de respuestas con error del servidor."),
        panel(4, "Duration: latencia p95 y p50",
              [(f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{APP_TRAFFIC}}}[5m])))', "p95"),
               (f'histogram_quantile(0.50, sum by (le) (rate(http_server_requests_seconds_bucket{{{APP_TRAFFIC}}}[5m])))', "p50")],
              16, 1, unit="s", desc="D de RED: percentiles a partir del histograma de Micrometer."),
        panel(5, "Instancias reportando", [('count(process_uptime_seconds{application="$application"})', "instancias")],
              0, 9, w=4, h=5, kind="stat", desc="Réplicas del servicio de las que Prometheus está leyendo métricas."),
        panel(6, "Memoria heap usada",
              [('sum(jvm_memory_used_bytes{application="$application", area="heap"})', "usada"),
               ('sum(jvm_memory_max_bytes{application="$application", area="heap"})', "máximo")],
              4, 9, w=10, h=5, unit="bytes"),
        panel(7, "CPU del proceso", [('process_cpu_usage{application="$application"}', "cpu")],
              14, 9, w=10, h=5, unit="percentunit"),
    ]
    return dashboard("circleguard-servicios", "CircleGuard · Servicios (RED)", p, [var],
                     "Rate, Errors y Duration (p95) de cada servicio, más memoria y CPU. Elige uno o varios servicios arriba.")


def business_dashboard():
    fence = 'status=~"SUSPECT|PROBABLE|CONFIRMED"'
    p = [
        panel(1, "Promociones por minuto (por estado)",
              [(f'sum by (status) (rate(circleguard_promotions_total{{{fence}}}[5m])) * 60', "{{status}}")],
              0, 0, w=12, unit="short",
              desc="Personas que entran al cerco por minuto: la que reporta y sus contactos de nivel 1 y 2."),
        panel(2, "Tiempo de contención (p95 y promedio)",
              [('histogram_quantile(0.95, sum by (le) (rate(circleguard_containment_time_seconds_bucket[15m])))', "p95"),
               ('sum(rate(circleguard_containment_time_seconds_sum[15m])) / clamp_min(sum(rate(circleguard_containment_time_seconds_count[15m])), 1e-9)', "promedio")],
              12, 0, w=12, unit="s",
              desc="Desde que la persona envía el cuestionario con síntomas hasta que su cerco queda aplicado en promotion."),
        panel(3, "Check-ins por minuto (por resultado)",
              [('sum by (result) (rate(circleguard_gate_checkins_total[5m])) * 60', "{{result}}")],
              0, 8, w=12, unit="short", desc="Validaciones de QR en la puerta: entra, en cerco, QR inválido o estado no disponible."),
        panel(4, "Cuestionarios por minuto", [('sum by (symptoms) (rate(circleguard_surveys_submitted_total[5m])) * 60', "síntomas={{symptoms}}")],
              12, 8, w=12, unit="short"),
        panel(5, "Check-ins (última hora)", [('sum(increase(circleguard_gate_checkins_total[1h]))', "check-ins")],
              0, 16, w=6, h=5, kind="stat"),
        panel(6, "Accesos negados por riesgo (última hora)",
              [('sum(increase(circleguard_gate_checkins_total{result="denied_risk"}[1h]))', "negados")],
              6, 16, w=6, h=5, kind="stat"),
        panel(7, "Personas promovidas al cerco (última hora)",
              [(f'sum(increase(circleguard_promotions_total{{{fence}}}[1h]))', "personas")],
              12, 16, w=6, h=5, kind="stat"),
        panel(8, "Contención p95 (última hora)",
              [('histogram_quantile(0.95, sum by (le) (increase(circleguard_containment_time_seconds_bucket[1h])))', "p95")],
              18, 16, w=6, h=5, kind="stat", unit="s"),
    ]
    return dashboard("circleguard-negocio", "CircleGuard · Negocio", p,
                     desc="Métricas de negocio propias (Micrometer): promociones, tiempo de contención, check-ins y cuestionarios.")


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    for name, d in [("servicios-red.json", services_dashboard()), ("negocio.json", business_dashboard())]:
        (OUT / name).write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("escrito", OUT / name)
