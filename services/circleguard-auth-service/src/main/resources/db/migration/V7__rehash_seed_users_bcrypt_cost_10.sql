-- HU-24 (#31): las cuentas locales de prueba pasan de BCrypt costo 12 a costo 10 (contraseña "password").
-- Con costo 12, cada login cuesta ~250 ms de CPU: en un nodo de 2 vCPU (t3.large, HU-14) el login se saturaba en
-- 8,5 req/s con p95 de 5,5 s. El costo 10 sigue dentro de lo que recomienda OWASP (>= 10) y es 4 veces más barato.
-- Medición antes y después: docs/pruebas/informe-calidad.md.
UPDATE local_users SET password_hash = '$2a$10$AT4O3NKoUvXhn5SYAJQ5ceL873OuGoNRJHN0iXS9N5oKki7HjnVS.'
WHERE username IN ('staff_guard', 'health_user', 'super_admin');
