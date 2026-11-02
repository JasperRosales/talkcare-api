package com.talkcare.api.repository;

import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.appointment.Appointment;
import com.talkcare.api.domain.appointment.AppointmentStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentRepository {

    private static final String SELECT = """
            SELECT a.id, a.client_id, a.professional_id, a.scheduled_start, a.scheduled_end, a.status, a.notes,
                   a.created_at, st.code AS service_type,
                   cup.full_name AS client_name,
                   TRIM(COALESCE(p.display_title || ' ', '') || COALESCE(pup.full_name, '')) AS professional_name
            FROM appointments a
            JOIN service_types st       ON st.service_type_id = a.service_type_id
            JOIN professionals p        ON p.user_id = a.professional_id
            LEFT JOIN user_profiles cup ON cup.user_id = a.client_id
            LEFT JOIN user_profiles pup ON pup.user_id = a.professional_id
            """;

    private static final RowMapper<Appointment> MAPPER = (rs, n) -> new Appointment(
            rs.getInt("id"),
            rs.getInt("client_id"),
            Rows.text(rs, "client_name"),
            rs.getInt("professional_id"),
            Rows.text(rs, "professional_name"),
            ServiceType.valueOf(rs.getString("service_type")),
            Rows.instant(rs, "scheduled_start"),
            Rows.instant(rs, "scheduled_end"),
            AppointmentStatus.valueOf(rs.getString("status")),
            rs.getString("notes"),
            Rows.instant(rs, "created_at"));

    private final JdbcClient jdbc;

    public AppointmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NewAppointment(int clientId, int professionalId, int serviceTypeId, Instant start, Instant end,
                                 String notes, int createdBy) {
    }

    public int create(NewAppointment a) {
        return jdbc.sql("""
                        INSERT INTO appointments (client_id, professional_id, service_type_id, scheduled_start,
                                                  scheduled_end, notes, created_by)
                        VALUES (:clientId, :professionalId, :serviceTypeId, :start, :end, :notes, :createdBy)
                        RETURNING id
                        """)
                .param("clientId", a.clientId())
                .param("professionalId", a.professionalId())
                .param("serviceTypeId", a.serviceTypeId())
                .param("start", Timestamp.from(a.start()))
                .param("end", Timestamp.from(a.end()))
                .param("notes", a.notes())
                .param("createdBy", a.createdBy())
                .query(Integer.class)
                .single();
    }

    public Optional<Appointment> findById(int id) {
        return jdbc.sql(SELECT + " WHERE a.id = :id").param("id", id).query(MAPPER).optional();
    }

    /** Upcoming first, then the most recent past ones. */
    public List<Appointment> listForClient(int clientId) {
        return jdbc.sql(SELECT + " WHERE a.client_id = :id" + ORDER).param("id", clientId).query(MAPPER).list();
    }

    public List<Appointment> listForProfessional(int professionalId) {
        return jdbc.sql(SELECT + " WHERE a.professional_id = :id" + ORDER).param("id", professionalId).query(MAPPER).list();
    }

    public List<Appointment> listBetween(Instant from, Instant to) {
        return jdbc.sql(SELECT + " WHERE a.scheduled_start >= :from AND a.scheduled_start < :to ORDER BY a.scheduled_start")
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(MAPPER)
                .list();
    }

    public List<Appointment> listUpcoming(int limit) {
        return jdbc.sql(SELECT + " WHERE a.scheduled_end >= now() ORDER BY a.scheduled_start LIMIT :limit")
                .param("limit", limit)
                .query(MAPPER)
                .list();
    }

    private static final String ORDER = """
             ORDER BY (a.scheduled_end < now()) ASC,
                      CASE WHEN a.scheduled_end >= now() THEN a.scheduled_start END ASC,
                      a.scheduled_start DESC
             LIMIT 100
            """;

    public void updateStatus(int id, AppointmentStatus status) {
        jdbc.sql("UPDATE appointments SET status = :status WHERE id = :id")
                .param("id", id)
                .param("status", status.name())
                .update();
    }

    /** Days of the month (1-31) with at least one non-cancelled appointment. */
    public List<Integer> daysWithAppointments(int year, int month, ZoneId zone) {
        return jdbc.sql("""
                        SELECT DISTINCT EXTRACT(DAY FROM scheduled_start AT TIME ZONE CAST(:tz AS text))::int AS day
                        FROM appointments
                        WHERE scheduled_start >= make_timestamptz(:year, :month, 1, 0, 0, 0, CAST(:tz AS text))
                          AND scheduled_start <  make_timestamptz(:year, :month, 1, 0, 0, 0, CAST(:tz AS text)) + INTERVAL '1 month'
                          AND status <> 'CANCELLED'
                        ORDER BY day
                        """)
                .param("year", year)
                .param("month", month)
                .param("tz", zone.getId())
                .query(Integer.class)
                .list();
    }

    public int countToday(ZoneId zone) {
        return jdbc.sql("""
                        SELECT COUNT(*)::int FROM appointments
                        WHERE (scheduled_start AT TIME ZONE CAST(:tz AS text))::date = (now() AT TIME ZONE CAST(:tz AS text))::date
                          AND status <> 'CANCELLED'
                        """)
                .param("tz", zone.getId())
                .query(Integer.class)
                .single();
    }
}
