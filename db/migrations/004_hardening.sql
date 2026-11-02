-- schema.md §8 — optional hardening (Neon supports btree_gist)
-- Generated from docs/schema.md. Each migration runs inside a single transaction.

-- Enable overlap protection for appointments (requires the btree_gist extension).
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_overlap
    EXCLUDE USING gist (
        professional_id WITH =,
        tstzrange(scheduled_start, scheduled_end) WITH &&
    )
    WHERE (status IN ('SCHEDULED', 'CONFIRMED'));

-- Case-insensitive uniqueness is already enforced by lower() indexes.
-- Optional: prevent an admin from reviewing their own verification request.
CREATE OR REPLACE FUNCTION prevent_self_verification_review() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.reviewed_by IS NOT NULL AND NEW.reviewed_by = NEW.user_id THEN
        RAISE EXCEPTION 'A user cannot review their own identity verification'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER identity_verifications_no_self_review
    BEFORE INSERT OR UPDATE OF reviewed_by ON identity_verifications
    FOR EACH ROW EXECUTE FUNCTION prevent_self_verification_review();
