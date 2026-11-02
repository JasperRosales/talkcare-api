-- Concern types offered by the desktop report form that are not in the schema.md §5 examples.
-- Mapped to the service whose professional type handles them.

INSERT INTO concern_types (name, service_type_id)
SELECT v.name, st.service_type_id
FROM (VALUES
    ('Mental Health Crisis', 'THOMATT'),
    ('Harassment / Abuse',   'JUSTICE_CARE')
) AS v(name, service_code)
JOIN service_types st ON st.code = v.service_code
ON CONFLICT (name) DO NOTHING;
