package com.talkcare.api.chatbot;

import com.talkcare.api.config.TalkCareProperties;
import com.talkcare.api.error.ServiceUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Calls Gemini's {@code generateContent}. The key stays on the server (GEMINI_API_KEY). Models are
 * tried in order; when every model is busy the list is retried once. Google's strictest safety
 * thresholds are always applied.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /** Give up before the desktop's 90-second request timeout. */
    private static final Duration DEADLINE = Duration.ofSeconds(60);
    private static final String[] SAFETY_CATEGORIES = {
            "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT"};
    private static final String BUSY = "The TalkCare Chatbot is busy right now. Please try again in a moment.";

    /** The model's answer, or {@code blocked} when Gemini's safety filters stopped it. */
    public record Reply(String text, boolean blocked) {
    }

    private final TalkCareProperties.Gemini settings;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public GeminiClient(TalkCareProperties properties, JsonMapper json) {
        this.settings = properties.gemini();
        this.json = json;
    }

    public boolean isConfigured() {
        return settings.isConfigured();
    }

    public Reply generate(String systemPrompt, List<ChatTurn> history) {
        if (!isConfigured()) throw new ServiceUnavailableException("The TalkCare Chatbot is not set up yet.");
        String body = requestBody(systemPrompt, history);
        long deadline = System.nanoTime() + DEADLINE.toNanos();
        for (int pass = 0; pass < 2; pass++) {
            for (String model : settings.models()) {
                if (System.nanoTime() > deadline) throw new ServiceUnavailableException(BUSY);
                HttpResponse<String> response = call(model, body);
                if (response == null) continue;
                int status = response.statusCode();
                if (status / 100 == 2) return parse(response.body());
                log.warn("Gemini {} answered HTTP {}", model, status);
                if (status != 404 && status != 429 && status < 500) {
                    throw new ServiceUnavailableException("The TalkCare Chatbot could not answer right now.");
                }
            }
            if (pass == 0) sleep();
        }
        throw new ServiceUnavailableException(BUSY);
    }

    private HttpResponse<String> call(String model, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT.formatted(model)))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", settings.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Gemini {} unreachable: {}", model, e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException(BUSY);
        }
    }

    private String requestBody(String systemPrompt, List<ChatTurn> history) {
        ObjectNode root = json.createObjectNode();
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", systemPrompt);
        ArrayNode contents = root.putArray("contents");
        for (ChatTurn turn : history) {
            ObjectNode content = contents.addObject();
            content.put("role", turn.fromUser() ? "user" : "model");
            content.putArray("parts").addObject().put("text", turn.text());
        }
        ArrayNode safety = root.putArray("safetySettings");
        for (String category : SAFETY_CATEGORIES) {
            safety.addObject().put("category", category).put("threshold", "BLOCK_LOW_AND_ABOVE");
        }
        root.putObject("generationConfig").put("temperature", 0.3).put("topP", 0.9).put("maxOutputTokens", 2048);
        return json.writeValueAsString(root);
    }

    private Reply parse(String body) {
        JsonNode root = json.readTree(body);
        if (root.path("promptFeedback").hasNonNull("blockReason")) return new Reply("", true);
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asString("");
        if (List.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION").contains(finish)) {
            return new Reply("", true);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.path("thought").asBoolean(false)) continue;
            text.append(part.path("text").asString(""));
        }
        return new Reply(text.toString().trim(), false);
    }

    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
