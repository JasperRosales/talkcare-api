package com.talkcare.api.repository;

import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Appointment statistics for the admin dashboard; dates are evaluated in the app time zone. */
@Repository
public class AppointmentRepository {

    private final JdbcClient jdbc;

    public AppointmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
