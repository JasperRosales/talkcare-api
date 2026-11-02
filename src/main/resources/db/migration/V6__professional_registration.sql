-- Attorneys and psychiatrists can register themselves. Like clients, they submit an identity
-- verification (a photo of their IBP/PRC ID); an administrator approves it before they are
-- assigned clients (professionals.is_accepting_clients starts FALSE for self-registered accounts).

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ATTORNEY', 'PSYCHIATRIST') AND p.code = 'verifications.submit'
ON CONFLICT DO NOTHING;
