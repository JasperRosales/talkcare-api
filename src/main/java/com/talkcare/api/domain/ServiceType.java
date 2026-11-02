package com.talkcare.api.domain;

import java.util.Arrays;
import java.util.Optional;

/** {@code service_types.code}; each service owns its conversation title and welcome message. */
public enum ServiceType {

    JUSTICE_CARE("JusticeCare Support") {
        @Override
        public String welcomeMessage(String professionalName) {
            return "Hello, I am " + professionalName
                    + " from JusticeCare. How can we support and protect your rights today?";
        }
    },
    THOMATT("ThoMatt Consultation") {
        @Override
        public String welcomeMessage(String professionalName) {
            return "Welcome to ThoMatt. I am " + professionalName
                    + ". This is a safe and confidential environment. Tell me your thoughts...";
        }
    };

    private final String conversationTitle;

    ServiceType(String conversationTitle) {
        this.conversationTitle = conversationTitle;
    }

    public String conversationTitle() {
        return conversationTitle;
    }

    /** First message the assigned professional "sends" when a conversation opens. */
    public abstract String welcomeMessage(String professionalName);

    /** Exact-code lookup ({@code "THOMATT"}), as the Express API accepted. */
    public static Optional<ServiceType> fromCode(String code) {
        return Arrays.stream(values()).filter(type -> type.name().equals(code)).findFirst();
    }
}
