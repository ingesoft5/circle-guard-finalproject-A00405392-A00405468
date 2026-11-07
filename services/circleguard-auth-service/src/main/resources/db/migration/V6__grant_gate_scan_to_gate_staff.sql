-- El rol GATE_STAFF (guardias) no tenía ningún permiso: sin gate:scan la app no muestra el escáner
-- de la entrada a nadie, ni siquiera a super_admin (que tiene todos los roles).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'GATE_STAFF' AND p.name = 'gate:scan'
ON CONFLICT DO NOTHING;
