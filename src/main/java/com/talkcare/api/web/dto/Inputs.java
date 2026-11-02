package com.talkcare.api.web.dto;

/** Shared request-field rules. */
public final class Inputs {

    /** Same rule as the Express API: something@something.tld without whitespace. */
    public static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    public static final String EMAIL_MESSAGE = "Please enter a valid email address.";
    public static final String TOO_LONG = "A field is longer than {max} characters.";

    private Inputs() {
    }

    /** Trims surrounding whitespace; null stays null. Passwords are never trimmed. */
    public static String trim(String value) {
        return value == null ? null : value.strip();
    }

    /** Trims, and turns an empty value into null (an optional field that was left blank). */
    public static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
