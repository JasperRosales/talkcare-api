package com.talkcare.api.repository;

import com.talkcare.api.domain.Role;
import com.talkcare.api.domain.chat.Message;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class MessageRepository {

    private static final String MESSAGE_SELECT = """
            SELECT m.id, m.conversation_id, m.sender_id, m.message_text, m.is_auto_reply, m.created_at,
                   up.full_name AS sender_name, u.username AS sender_username, r.code AS sender_role_code,
                   COALESCE(
                     (SELECT json_agg(json_build_object('url', a.file_url, 'mimeType', a.mime_type, 'byteSize', a.byte_size)
                                      ORDER BY a.id)
                      FROM message_attachments a WHERE a.message_id = m.id),
                     '[]'::json) AS attachments
            FROM messages m
            LEFT JOIN users u          ON u.id = m.sender_id
            LEFT JOIN roles r          ON r.role_id = u.role_id
            LEFT JOIN user_profiles up ON up.user_id = m.sender_id
            """;

    private static final TypeReference<List<Message.Attachment>> ATTACHMENTS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final RowMapper<Message> mapper;

    public MessageRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.mapper = (rs, n) -> new Message(
                rs.getInt("id"),
                rs.getInt("conversation_id"),
                Rows.integer(rs, "sender_id"),
                rs.getString("sender_name"),
                rs.getString("sender_username"),
                Rows.enumValue(rs, "sender_role_code", Role.class),
                rs.getString("message_text"),
                rs.getBoolean("is_auto_reply"),
                json.readValue(rs.getString("attachments"), ATTACHMENTS),
                Rows.instant(rs, "created_at"));
    }

    /** A message to insert; auto-replies have no sender. */
    public record NewMessage(int conversationId, Integer senderId, String text, boolean autoReply, Message.Attachment attachment) {

        public static NewMessage from(int conversationId, int senderId, String text, Message.Attachment attachment) {
            return new NewMessage(conversationId, senderId, text, false, attachment);
        }

        public static NewMessage autoReply(int conversationId) {
            return new NewMessage(conversationId, null, Message.AUTO_REPLY_TEXT, true, null);
        }
    }

    public List<Message> listByConversation(int conversationId) {
        return jdbc.sql(MESSAGE_SELECT + " WHERE m.conversation_id = :id ORDER BY m.created_at, m.id")
                .param("id", conversationId)
                .query(mapper)
                .list();
    }

    public List<Message> listByIds(List<Integer> ids) {
        return jdbc.sql(MESSAGE_SELECT + " WHERE m.id IN (:ids) ORDER BY m.created_at, m.id")
                .param("ids", ids)
                .query(mapper)
                .list();
    }

    /** Inserts a message and its optional attachment. Call inside a transaction. */
    public int create(NewMessage message) {
        int id = jdbc.sql("""
                        INSERT INTO messages (conversation_id, sender_id, message_text, is_auto_reply)
                        VALUES (:conversationId, :senderId, :text, :autoReply) RETURNING id
                        """)
                .param("conversationId", message.conversationId())
                .param("senderId", message.senderId())
                .param("text", message.text() == null || message.text().isEmpty() ? null : message.text())
                .param("autoReply", message.autoReply())
                .query(Integer.class)
                .single();
        Message.Attachment attachment = message.attachment();
        if (attachment != null) {
            jdbc.sql("""
                            INSERT INTO message_attachments (message_id, file_url, mime_type, byte_size)
                            VALUES (:id, :url, :mimeType, :byteSize)
                            """)
                    .param("id", id)
                    .param("url", attachment.url())
                    .param("mimeType", attachment.mimeType())
                    .param("byteSize", attachment.byteSize())
                    .update();
        }
        return id;
    }
}
