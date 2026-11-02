-- schema.md §5 — roles, services, lookups, permissions
-- Generated from docs/schema.md. Applied by Flyway, one transaction per migration.

-- Roles
INSERT INTO roles (code, display_name, is_professional) VALUES
    ('CLIENT',       'Client',         FALSE),
    ('ATTORNEY',     'Attorney',       TRUE),
    ('PSYCHIATRIST', 'Psychiatrist',   TRUE),
    ('ADMIN',        'Administrator',  FALSE)
ON CONFLICT (code) DO NOTHING;

-- Services
INSERT INTO service_types (code, display_name, required_professional_type) VALUES
    ('JUSTICE_CARE', 'JusticeCare Legal Rights & Victim Support', 'ATTORNEY'),
    ('THOMATT',      'ThoMatt Mental Health & Well-being',        'PSYCHIATRIST')
ON CONFLICT (code) DO NOTHING;

-- IDs accepted by the UI
INSERT INTO id_types (name) VALUES
    ('Student ID'), ('National ID (PhilSys)'), ('Driver''s License'),
    ('Passport'), ('Postal ID'), ('PRC ID'), ('UMID')
ON CONFLICT (name) DO NOTHING;

-- Concern catalog (examples; admin can extend)
INSERT INTO concern_types (name, service_type_id)
SELECT v.name, st.service_type_id
FROM (VALUES
    ('Domestic Injustice',      'JUSTICE_CARE'),
    ('Workplace Discrimination','JUSTICE_CARE'),
    ('Legal Rights Violation',  'JUSTICE_CARE'),
    ('Family Dispute',          'JUSTICE_CARE'),
    ('Student Mental Distress', 'THOMATT'),
    ('Anxiety / Depression',    'THOMATT'),
    ('Trauma / PTSD',           'THOMATT'),
    ('Other',                   NULL)
) AS v(name, service_code)
LEFT JOIN service_types st ON st.code = v.service_code
ON CONFLICT (name) DO NOTHING;

-- Attorney practice areas
INSERT INTO practice_areas (name) VALUES
    ('Family Law'), ('Labor Law'), ('Criminal Law'),
    ('Civil Rights'), ('Victim Support'), ('VAWC')
ON CONFLICT (name) DO NOTHING;

-- Psychiatric specializations
INSERT INTO specializations (name) VALUES
    ('Clinical Psychiatry'), ('Child & Adolescent Psychiatry'), ('Trauma & PTSD'),
    ('Anxiety Disorders'), ('Mood Disorders'), ('Addiction Medicine')
ON CONFLICT (name) DO NOTHING;

-- Permissions
INSERT INTO permissions (code, description) VALUES
    ('reports.create',                 'Submit a concern report'),
    ('reports.read.own',               'Read own reports'),
    ('reports.read.assigned',          'Read reports assigned to the professional'),
    ('reports.read.all',               'Read every report'),
    ('reports.update.status',          'Change report status'),
    ('conversations.create',           'Open a conversation'),
    ('conversations.read.own',         'Read own conversations'),
    ('conversations.read.assigned',    'Read conversations assigned to the professional'),
    ('messages.send',                  'Send messages as a client'),
    ('messages.reply',                 'Reply as a professional'),
    ('appointments.book.own',          'Book own appointments'),
    ('appointments.read.assigned',     'Read assigned appointments'),
    ('appointments.manage.assigned',   'Confirm/cancel assigned appointments'),
    ('users.read',                     'List user accounts'),
    ('users.manage',                   'Create/update/suspend user accounts'),
    ('verifications.submit',           'Submit identity verification'),
    ('verifications.review',           'Approve/reject identity verification'),
    ('admin.dashboard',                'Access the admin dashboard'),
    ('admin.activity.read',            'Read the admin activity log')
ON CONFLICT (code) DO NOTHING;

-- Client permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'CLIENT' AND p.code IN (
    'reports.create', 'reports.read.own',
    'conversations.create', 'conversations.read.own',
    'messages.send', 'appointments.book.own', 'verifications.submit')
ON CONFLICT DO NOTHING;

-- Professional permissions (attorney + psychiatrist)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.code IN ('ATTORNEY', 'PSYCHIATRIST') AND p.code IN (
    'reports.read.assigned', 'conversations.read.assigned',
    'messages.reply', 'appointments.read.assigned', 'appointments.manage.assigned')
ON CONFLICT DO NOTHING;

-- Admin: every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN'
ON CONFLICT DO NOTHING;
