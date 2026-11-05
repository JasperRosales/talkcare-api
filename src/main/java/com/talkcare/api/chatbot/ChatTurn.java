package com.talkcare.api.chatbot;

import java.time.Instant;

/** One chatbot message; {@code fromUser} is false for the bot's replies. */
public record ChatTurn(boolean fromUser, String text, Instant createdAt) {
}
