package com.ticketrush.outbox;

import java.time.Clock;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketrush.common.config.TicketRushProperties;
import com.ticketrush.common.time.Times;

/** Xoá outbox đã gửi lâu hơn {@code ticketrush.outbox.retention} (mặc định 7 ngày). */
@Component
@ConditionalOnBooleanProperty("ticketrush.jobs.enabled")
class OutboxCleanup {

    private final NamedParameterJdbcTemplate jdbc;
    private final TicketRushProperties properties;
    private final Clock clock;

    OutboxCleanup(NamedParameterJdbcTemplate jdbc, TicketRushProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    void deletePublished() {
        jdbc.update("DELETE FROM outbox_events WHERE published_at < :cutoff",
                Map.of("cutoff", Times.utc(clock.instant().minus(properties.outbox().retention()))));
    }
}
