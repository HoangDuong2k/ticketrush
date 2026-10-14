package com.ticketrush.identity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ticketrush.common.time.Times;

@Repository
class OrganizerAccounts {

    private final NamedParameterJdbcTemplate jdbc;

    OrganizerAccounts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, String name, Instant now) {
        jdbc.update("INSERT INTO organizers (id, name, created_at) VALUES (:id, :name, :now)",
                Map.of("id", id, "name", name, "now", Times.utc(now)));
    }

    boolean exists(UUID id) {
        List<Integer> found = jdbc.queryForList("SELECT 1 FROM organizers WHERE id = :id",
                Map.of("id", id), Integer.class);
        return !found.isEmpty();
    }
}
