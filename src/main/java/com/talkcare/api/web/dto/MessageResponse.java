package com.talkcare.api.web.dto;

import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.chat.Message;
import java.time.Instant;
import java.util.List;

/** {@code senderRole} is SYSTEM, CLIENT, ADMIN or PROFESSIONAL. */
public record MessageResponse(
        int id,
        int conversationId,
        Integer senderId,
        String senderName,
        String senderRole,
        String messageText,
        boolean autoReply,
        String attachmentUrl,
        List<Message.Attachment> attachments,
        Instant createdAt) {

    static final String AUTO_ASSISTANT = "TalkCare Auto-Assistant";

    public static MessageResponse from(Message m) {
        List<Message.Attachment> attachments = m.attachments() == null ? List.of() : m.attachments();
        return new MessageResponse(
                m.id(),
                m.conversationId(),
                m.senderId(),
                senderName(m),
                senderRole(m),
                m.text() == null ? "" : m.text(),
                m.autoReply(),
                attachments.isEmpty() ? "" : attachments.getFirst().url(),
                attachments,
                m.createdAt());
    }

    private static String senderName(Message m) {
        if (m.autoReply()) {
            return AUTO_ASSISTANT;
        }
        if (m.senderName() != null && !m.senderName().isEmpty()) {
            return m.senderName();
        }
        return m.senderUsername() == null ? "" : m.senderUsername();
    }

    private static String senderRole(Message m) {
        if (m.autoReply()) {
            return "SYSTEM";
        }
        if (m.senderRole() == Role.CLIENT || m.senderRole() == Role.ADMIN) {
            return m.senderRole().name();
        }
        return "PROFESSIONAL";
    }
}
