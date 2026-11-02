package com.talkcare.api.repository;

import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.chat.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepository {

    private static final String CONVERSATION_SELECT = """
            SELECT c.id, c.client_id, c.professional_id, c.title, c.status, c.created_at, c.updated_at,
                   st.code AS service_type,
                   cup.full_name AS client_name,
                   pup.full_name AS professional_name,
                   p.display_title AS professional_title,
                   p.professional_type
            FROM conversations c
            JOIN service_types st       ON st.service_type_id = c.service_type_id
            JOIN professionals p        ON p.user_id = c.professional_id
            LEFT JOIN user_profiles cup ON cup.user_id = c.client_id
            LEFT JOIN user_profiles pup ON pup.user_id = c.professional_id
            """;

    private static final RowMapper<Conversation> MAPPER = (rs, n) -> new Conversation(
            rs.getInt("id"),
            rs.getInt("client_id"),
            Rows.text(rs, "client_name"),
            rs.getInt("professional_id"),
            Rows.text(rs, "professional_name"),
            Rows.text(rs, "professional_title"),
            rs.getString("professional_type"),
            ServiceType.valueOf(rs.getString("service_type")),
            Rows.text(rs, "title"),
            rs.getString("status"),
            Rows.instant(rs, "created_at"),
            Rows.instant(rs, "updated_at"));

    private final JdbcClient jdbc;

    public ConversationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record NewConversation(int clientId, int professionalId, int serviceTypeId, String title) {
    }

    public Optional<Conversation> findById(int id) {
        return jdbc.sql(CONVERSATION_SELECT + " WHERE c.id = :id").param("id", id).query(MAPPER).optional();
    }

    public Optional<Conversation> findOpen(int clientId, ServiceType service) {
        return jdbc.sql(CONVERSATION_SELECT + " WHERE c.client_id = :clientId AND st.code = :code AND c.status = 'OPEN'")
                .param("clientId", clientId)
                .param("code", service.name())
                .query(MAPPER)
                .optional();
    }

    /** Least-loaded accepting, active professional of the type the service requires. */
    public Optional<Integer> pickProfessional(ServiceType service) {
        return jdbc.sql("""
                        SELECT p.user_id
                        FROM professionals p
                        JOIN service_types st ON st.required_professional_type = p.professional_type
                        JOIN users u ON u.id = p.user_id
                        WHERE st.code = :code AND p.is_accepting_clients AND u.status NOT IN ('SUSPENDED', 'DEACTIVATED')
                        ORDER BY (SELECT COUNT(*) FROM conversations c WHERE c.professional_id = p.user_id AND c.status = 'OPEN'),
                                 p.user_id
                        LIMIT 1
                        """)
                .param("code", service.name())
                .query(Integer.class)
                .optional();
    }

    public int create(NewConversation conversation) {
        return jdbc.sql("""
                        INSERT INTO conversations (client_id, professional_id, service_type_id, title)
                        VALUES (:clientId, :professionalId, :serviceTypeId, :title) RETURNING id
                        """)
                .param("clientId", conversation.clientId())
                .param("professionalId", conversation.professionalId())
                .param("serviceTypeId", conversation.serviceTypeId())
                .param("title", conversation.title())
                .query(Integer.class)
                .single();
    }

    public List<Conversation> listForProfessional(int professionalId) {
        return jdbc.sql(CONVERSATION_SELECT + " WHERE c.professional_id = :id ORDER BY c.updated_at DESC, c.id DESC")
                .param("id", professionalId)
                .query(MAPPER)
                .list();
    }

    public List<Conversation> listAll() {
        return jdbc.sql(CONVERSATION_SELECT + " ORDER BY c.updated_at DESC, c.id DESC LIMIT 200").query(MAPPER).list();
    }

    public void touch(int id) {
        jdbc.sql("UPDATE conversations SET updated_at = now() WHERE id = :id").param("id", id).update();
    }
}
