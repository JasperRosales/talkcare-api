package com.talkcare.api.web.dto;

/** Small fixed-shape response bodies. */
public final class SimpleResponses {

    private SimpleResponses() {
    }

    public record Success(boolean success) {
        public static final Success OK = new Success(true);
    }

    public record Info(String message) {
    }

    public record Photo(String url) {
    }

    public record VerificationSubmitted(boolean success, String status) {
    }

    public record Health(boolean ok, String database, String message) {
    }

    public record Root(String name, String docs) {
    }

    public record Metrics(int totalUsers, int pendingApprovals, int sessionToday) {
    }

    public record Schedule(int year, int month, String monthName, java.util.List<Integer> days) {
    }
}
