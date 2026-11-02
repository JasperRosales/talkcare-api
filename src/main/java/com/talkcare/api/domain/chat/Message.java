package com.talkcare.api.domain.chat;

import com.talkcare.api.domain.Role;
import java.time.Instant;
import java.util.List;

/** A chat message; system auto-replies have no sender. */
public record Message(
        int id,
        int conversationId,
        Integer senderId,
        String senderName,
        String senderUsername,
        Role senderRole,
        String text,
        boolean autoReply,
        List<Attachment> attachments,
        Instant createdAt) {

    public static final String AUTO_REPLY_TEXT =
            "thank you for your message, please wait for the reply of the assigned Professional";

    public record Attachment(String url, String mimeType, Long byteSize) {
    }
}
