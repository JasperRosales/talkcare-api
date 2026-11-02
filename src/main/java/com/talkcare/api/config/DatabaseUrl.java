package com.talkcare.api.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts the {@code postgresql://user:password@host/db?sslmode=require} strings that the Neon
 * console hands out (and that the Express version used) into JDBC settings. A value that already
 * starts with {@code jdbc:} is used unchanged.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    /** libpq parameter names that pgjdbc spells differently. */
    private static final Map<String, String> PARAMETER_ALIASES = Map.of("channel_binding", "channelBinding");

    public static DatabaseUrl parse(String value) {
        String raw = value == null ? "" : value.trim();
        if (raw.startsWith("jdbc:")) {
            return new DatabaseUrl(raw, null, null);
        }
        if (!raw.startsWith("postgres://") && !raw.startsWith("postgresql://")) {
            throw new IllegalArgumentException("DATABASE_URL must be a postgresql:// connection string.");
        }
        URI uri = URI.create(raw.replaceFirst("^postgres(ql)?://", "postgresql://"));
        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            username = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }
        StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbc.append(':').append(uri.getPort());
        }
        jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
        List<String> params = new ArrayList<>();
        if (uri.getRawQuery() != null) {
            for (String param : uri.getRawQuery().split("&")) {
                if (param.isEmpty()) {
                    continue;
                }
                String[] kv = param.split("=", 2);
                String name = PARAMETER_ALIASES.getOrDefault(kv[0], kv[0]);
                params.add(kv.length > 1 ? name + "=" + kv[1] : name);
            }
        }
        if (!params.isEmpty()) {
            jdbc.append('?').append(String.join("&", params));
        }
        return new DatabaseUrl(jdbc.toString(), username, password);
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
