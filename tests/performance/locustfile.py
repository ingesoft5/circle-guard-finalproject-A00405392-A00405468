"""Prueba de carga de CircleGuard (HU-24): validación de acceso en la puerta y envío del cuestionario.

Objetivo declarado antes de medir: docs/pruebas/rendimiento-objetivo.md. Todo pasa por el nginx del web, como un
cliente real. Correr con tests/performance/run.sh (Locust en un contenedor de la misma red que el compose).
"""
import random
import time
import uuid

from locust import HttpUser, between, events, task

USERS = ["health_user", "staff_guard", "super_admin"]   # cuentas locales sembradas por auth (V2__seed_test_users.sql)
QR_REFRESH_SECONDS = 240                                 # el QR vence a los 5 min (qr.expiration = 300000 ms)


class GateUser(HttpUser):
    """Un guardia validando QR en la puerta."""
    weight = 5
    wait_time = between(0.5, 1.5)

    def on_start(self):
        self.username = random.choice(USERS)
        self.qr, self.qr_at = None, 0.0

    def _login_and_qr(self):
        with self.client.post("/api/auth/api/v1/auth/login", name="auth: login",
                              json={"username": self.username, "password": "password"}, catch_response=True) as r:
            if r.status_code != 200:
                r.failure(f"login {r.status_code}")
                return
            token = r.json()["token"]
        with self.client.get("/api/auth/api/v1/auth/qr/generate", name="auth: qr",
                             headers={"Authorization": f"Bearer {token}"}, catch_response=True) as r:
            if r.status_code != 200:
                r.failure(f"qr {r.status_code}")
                return
            self.qr, self.qr_at = r.json()["qrToken"], time.time()

    @task
    def validate(self):
        if self.qr is None or time.time() - self.qr_at > QR_REFRESH_SECONDS:
            self._login_and_qr()
            if self.qr is None:
                return
        with self.client.post("/api/gateway/api/v1/gate/validate", name="puerta: validar QR",
                              json={"token": self.qr}, catch_response=True) as r:
            # La puerta responde 200 tanto para "entra" como para "no entra": es error solo si no hay decisión.
            if r.status_code != 200 or "status" not in r.json():
                r.failure(f"validate {r.status_code}")


class SurveyUser(HttpUser):
    """Una persona enviando el cuestionario diario desde el celular.

    Peso 2 (y no 1): con pausas de ~1 s cada usuario genera ~1 req/s, así que 10 usuarios no alcanzan las 14 req/s
    del objetivo aunque el sistema responda en milisegundos. La primera corrida lo mostró (8,8 req/s con p95 de 67 ms).
    """
    weight = 2
    wait_time = between(0.5, 1.5)

    def on_start(self):
        r = self.client.get("/api/form/api/v1/questionnaires/active", name="form: cuestionario activo")
        self.question_id = r.json()["questions"][0]["id"] if r.status_code == 200 else None

    @task
    def submit(self):
        if self.question_id is None:
            return
        answer = "YES" if random.random() < 1 / 3 else "NO"
        self.client.post("/api/form/api/v1/surveys", name="cuestionario: enviar",
                         json={"anonymousId": str(uuid.uuid4()), "responses": {self.question_id: answer}})


class LoginUser(HttpUser):
    """Escenario aparte (no entra en la corrida por defecto): personas iniciando sesión para sacar su QR.

    Se corre con CLASSES=LoginUser tests/performance/run.sh ... para medir el login, que es lo más lento del flujo.
    """
    wait_time = between(0.5, 1.5)

    @task
    def login(self):
        self.client.post("/api/auth/api/v1/auth/login", name="auth: login",
                         json={"username": random.choice(USERS), "password": "password"})


@events.quitting.add_listener
def _check_objective(environment, **_):
    """Compara contra el objetivo y deja el código de salida en 1 si no se cumple (útil en el pipeline)."""
    objective = {"puerta: validar QR": (33, 0.300), "cuestionario: enviar": (14, 0.800)}
    failed = False
    for name, (min_rps, max_p95) in objective.items():
        s = environment.stats.get(name, "POST")
        if s.num_requests == 0:   # escenario que no corrió en esta ejecución (p. ej. solo LoginUser)
            continue
        p95 = s.get_response_time_percentile(0.95) / 1000
        rps = s.total_rps
        err = s.fail_ratio
        ok = rps >= min_rps and p95 <= max_p95 and err < 0.01
        failed |= not ok
        print(f"[objetivo] {name}: {rps:.1f} req/s (>= {min_rps}), p95 {p95 * 1000:.0f} ms (<= {max_p95 * 1000:.0f}), "
              f"errores {err:.2%} (< 1 %) -> {'CUMPLE' if ok else 'NO CUMPLE'}")
    environment.process_exit_code = 1 if failed else 0
