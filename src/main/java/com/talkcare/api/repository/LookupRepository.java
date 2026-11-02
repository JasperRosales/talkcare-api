package com.talkcare.api.repository;

import com.talkcare.api.domain.ServiceType;
import com.talkcare.api.domain.lookup.ConcernType;
import com.talkcare.api.domain.lookup.IdType;
import com.talkcare.api.domain.lookup.ServiceTypeInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reference tables: services, concern types, accepted ID types. */
@Repository
public class LookupRepository {

    private static final RowMapper<ServiceTypeInfo> SERVICE = (rs, n) -> new ServiceTypeInfo(
            rs.getInt("service_type_id"), ServiceType.valueOf(rs.getString("code")), rs.getString("display_name"));
    private static final RowMapper<ConcernType> CONCERN = (rs, n) -> new ConcernType(
            rs.getInt("concern_type_id"), rs.getString("name"), Rows.enumValue(rs, "service_code", ServiceType.class));
    private static final RowMapper<IdType> ID_TYPE = (rs, n) -> new IdType(rs.getInt("id_type_id"), rs.getString("name"));

    private static final String CONCERN_SELECT = """
            SELECT ct.concern_type_id, ct.name, st.code AS service_code
            FROM concern_types ct LEFT JOIN service_types st ON st.service_type_id = ct.service_type_id
            """;

    private final JdbcClient jdbc;

    public LookupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ServiceTypeInfo> findServiceType(ServiceType code) {
        return jdbc.sql("SELECT service_type_id, code, display_name FROM service_types WHERE code = :code")
                .param("code", code.name())
                .query(SERVICE)
                .optional();
    }

    public Optional<ConcernType> findConcernType(String name) {
        return jdbc.sql(CONCERN_SELECT + " WHERE LOWER(ct.name) = LOWER(:name)")
                .param("name", name)
                .query(CONCERN)
                .optional();
    }

    public Optional<IdType> findIdType(String name) {
        return jdbc.sql("SELECT id_type_id, name FROM id_types WHERE LOWER(name) = LOWER(:name)")
                .param("name", name)
                .query(ID_TYPE)
                .optional();
    }

    public List<ServiceTypeInfo> listServiceTypes() {
        return jdbc.sql("SELECT service_type_id, code, display_name FROM service_types ORDER BY service_type_id")
                .query(SERVICE)
                .list();
    }

    public List<ConcernType> listConcernTypes() {
        return jdbc.sql(CONCERN_SELECT + " ORDER BY ct.concern_type_id").query(CONCERN).list();
    }

    public List<IdType> listIdTypes() {
        return jdbc.sql("SELECT id_type_id, name FROM id_types ORDER BY id_type_id").query(ID_TYPE).list();
    }
}
