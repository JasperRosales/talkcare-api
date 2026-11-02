package com.talkcare.api.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** Null-safe column readers shared by the row mappers. */
final class Rows {

    private Rows() {
    }

    /** {@code timestamptz} as an Instant at millisecond precision (what the Express API returned). */
    static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant().truncatedTo(ChronoUnit.MILLIS);
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    static boolean bool(ResultSet rs, String column, boolean fallback) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? fallback : value;
    }

    static String text(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    static <E extends Enum<E>> E enumValue(ResultSet rs, String column, Class<E> type) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }
}
