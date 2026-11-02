package com.talkcare.api.error;

import java.util.Map;
import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Maps PostgreSQL SQLSTATEs raised by constraints and triggers to API errors. */
@Component
public class DatabaseErrorTranslator {

    private record Mapping(HttpStatus status, String code, String message) {
    }

    private static final String CHECK_VIOLATION = "23514";

    private static final Map<String, Mapping> MAPPINGS = Map.of(
            "23505", new Mapping(HttpStatus.CONFLICT, "CONFLICT", "That value is already in use (username, email, or license number)."),
            "23503", new Mapping(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A referenced record does not exist."),
            CHECK_VIOLATION, new Mapping(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request violates a data rule."),
            "23P01", new Mapping(HttpStatus.CONFLICT, "CONFLICT", "That time slot is already booked."),
            "22P02", new Mapping(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A value has an invalid format."));

    public record Translation(HttpStatus status, ErrorResponse body) {
    }

    public Optional<Translation> translate(Throwable error) {
        PSQLException psql = findCause(error);
        if (psql == null || psql.getSQLState() == null) {
            return Optional.empty();
        }
        Mapping mapping = MAPPINGS.get(psql.getSQLState());
        if (mapping == null) {
            return Optional.empty();
        }
        return Optional.of(new Translation(mapping.status(), new ErrorResponse(messageFor(psql, mapping), mapping.code())));
    }

    /** Trigger messages (RAISE EXCEPTION) are written for humans; constraint names are not. */
    private static String messageFor(PSQLException psql, Mapping mapping) {
        ServerErrorMessage server = psql.getServerErrorMessage();
        boolean raisedByTrigger = server != null && server.getWhere() != null && server.getWhere().contains("PL/pgSQL");
        if (CHECK_VIOLATION.equals(psql.getSQLState()) && raisedByTrigger && server.getMessage() != null) {
            return server.getMessage();
        }
        return mapping.message();
    }

    private static PSQLException findCause(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof PSQLException psql) {
                return psql;
            }
        }
        return null;
    }
}
