package com.talkcare.api.task;

import java.util.Arrays;
import java.util.Set;

/** A one-off command run instead of the HTTP server: {@code java -jar talkcare-api.jar <name>}. */
public interface CliTask {

    Set<String> NAMES = Set.of("migrate", "seed");

    /** The command-line word that selects this task. */
    String name();

    void run() throws Exception;

    static boolean isRequested(String[] args) {
        return Arrays.stream(args).anyMatch(NAMES::contains);
    }
}
