package com.talkcare.api.web;

import com.talkcare.api.service.SupportService;
import com.talkcare.api.web.dto.LookupsResponse;
import com.talkcare.api.web.dto.SimpleResponses.Health;
import com.talkcare.api.web.dto.SimpleResponses.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoints: root, health check, form reference data. */
@RestController
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    private final SupportService support;

    public SystemController(SupportService support) {
        this.support = support;
    }

    @GetMapping("/")
    public Root root() {
        return new Root("TalkCare API", "/api/health");
    }

    @GetMapping("/api/health")
    public ResponseEntity<Health> health() {
        try {
            support.checkDatabase();
            return ResponseEntity.ok(new Health(true, "connected", "Connected to the TalkCare database."));
        } catch (DataAccessException ex) {
            log.error("Health check failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new Health(false, "unreachable", "The TalkCare database is unreachable."));
        }
    }

    @GetMapping("/api/lookups")
    public LookupsResponse lookups() {
        return support.lookups();
    }
}
