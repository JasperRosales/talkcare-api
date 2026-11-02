-- schema.md §4 — integrity and updated_at triggers
-- Generated from docs/schema.md. Each migration runs inside a single transaction.

CREATE OR REPLACE FUNCTION assert_user_role(p_user_id INTEGER, p_role_code VARCHAR)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    actual_code VARCHAR(20);
BEGIN
    SELECT r.code INTO actual_code
    FROM users u
    JOIN roles r ON r.role_id = u.role_id
    WHERE u.id = p_user_id;

    IF actual_code IS NULL THEN
        RAISE EXCEPTION 'User % does not exist', p_user_id USING ERRCODE = '23503';
    END IF;

    IF actual_code <> p_role_code THEN
        RAISE EXCEPTION 'User % must have role %, but has %',
            p_user_id, p_role_code, actual_code
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION check_client_role() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM assert_user_role(NEW.user_id, 'CLIENT');
    RETURN NEW;
END;
$$;

CREATE TRIGGER clients_role_check
    BEFORE INSERT OR UPDATE OF user_id ON clients
    FOR EACH ROW EXECUTE FUNCTION check_client_role();

CREATE OR REPLACE FUNCTION check_admin_role() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM assert_user_role(NEW.user_id, 'ADMIN');
    RETURN NEW;
END;
$$;

CREATE TRIGGER admins_role_check
    BEFORE INSERT OR UPDATE OF user_id ON admins
    FOR EACH ROW EXECUTE FUNCTION check_admin_role();

CREATE OR REPLACE FUNCTION check_professional_role() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM assert_user_role(NEW.user_id, NEW.professional_type);
    RETURN NEW;
END;
$$;

CREATE TRIGGER professionals_role_check
    BEFORE INSERT OR UPDATE OF user_id, professional_type ON professionals
    FOR EACH ROW EXECUTE FUNCTION check_professional_role();

CREATE OR REPLACE FUNCTION check_attorney_type() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM assert_user_role(NEW.user_id, 'ATTORNEY');
    IF NOT EXISTS (
        SELECT 1 FROM professionals
        WHERE user_id = NEW.user_id AND professional_type = 'ATTORNEY'
    ) THEN
        RAISE EXCEPTION 'User % is not registered as an ATTORNEY professional', NEW.user_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER attorneys_type_check
    BEFORE INSERT OR UPDATE ON attorneys
    FOR EACH ROW EXECUTE FUNCTION check_attorney_type();

CREATE OR REPLACE FUNCTION check_psychiatrist_type() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM assert_user_role(NEW.user_id, 'PSYCHIATRIST');
    IF NOT EXISTS (
        SELECT 1 FROM professionals
        WHERE user_id = NEW.user_id AND professional_type = 'PSYCHIATRIST'
    ) THEN
        RAISE EXCEPTION 'User % is not registered as a PSYCHIATRIST professional', NEW.user_id
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER psychiatrists_type_check
    BEFORE INSERT OR UPDATE ON psychiatrists
    FOR EACH ROW EXECUTE FUNCTION check_psychiatrist_type();

CREATE OR REPLACE FUNCTION validate_service_assignment(
    p_client_id INTEGER,
    p_professional_id INTEGER,
    p_service_type_id SMALLINT
) RETURNS VOID
LANGUAGE plpgsql AS $$
DECLARE
    required_type VARCHAR(20);
    actual_type   VARCHAR(20);
BEGIN
    PERFORM assert_user_role(p_client_id, 'CLIENT');

    SELECT required_professional_type INTO required_type
    FROM service_types WHERE service_type_id = p_service_type_id;

    SELECT professional_type INTO actual_type
    FROM professionals WHERE user_id = p_professional_id;

    IF actual_type IS NULL THEN
        RAISE EXCEPTION 'Professional % does not exist', p_professional_id
            USING ERRCODE = '23503';
    END IF;

    IF actual_type <> required_type THEN
        RAISE EXCEPTION 'Service % requires a %, but user % is a %',
            p_service_type_id, required_type, p_professional_id, actual_type
            USING ERRCODE = '23514';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION validate_conversation() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM validate_service_assignment(NEW.client_id, NEW.professional_id, NEW.service_type_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER conversations_service_check
    BEFORE INSERT OR UPDATE OF client_id, professional_id, service_type_id ON conversations
    FOR EACH ROW EXECUTE FUNCTION validate_conversation();

CREATE OR REPLACE FUNCTION validate_appointment() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM validate_service_assignment(NEW.client_id, NEW.professional_id, NEW.service_type_id);
    RETURN NEW;
END;
$$;

CREATE TRIGGER appointments_service_check
    BEFORE INSERT OR UPDATE OF client_id, professional_id, service_type_id ON appointments
    FOR EACH ROW EXECUTE FUNCTION validate_appointment();

CREATE OR REPLACE FUNCTION validate_report() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM assert_user_role(NEW.client_id, 'CLIENT');
    IF NEW.assigned_professional_id IS NOT NULL THEN
        PERFORM validate_service_assignment(
            NEW.client_id, NEW.assigned_professional_id, NEW.service_type_id);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER reports_assignment_check
    BEFORE INSERT OR UPDATE OF client_id, assigned_professional_id, service_type_id ON reports
    FOR EACH ROW EXECUTE FUNCTION validate_report();

CREATE OR REPLACE FUNCTION touch_updated_at() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_touch_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER user_profiles_touch_updated_at
    BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER professionals_touch_updated_at
    BEFORE UPDATE ON professionals FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER reports_touch_updated_at
    BEFORE UPDATE ON reports FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER conversations_touch_updated_at
    BEFORE UPDATE ON conversations FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
CREATE TRIGGER appointments_touch_updated_at
    BEFORE UPDATE ON appointments FOR EACH ROW EXECUTE FUNCTION touch_updated_at();
