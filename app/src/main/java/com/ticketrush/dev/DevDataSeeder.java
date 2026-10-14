package com.ticketrush.dev;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.ticketrush.catalog.OrganizerCatalogService;
import com.ticketrush.catalog.OrganizerCatalogService.EventFields;
import com.ticketrush.catalog.OrganizerCatalogService.SeatRow;
import com.ticketrush.catalog.TicketKind;
import com.ticketrush.common.security.Role;
import com.ticketrush.identity.AccountService;
import com.ticketrush.identity.UserDirectory;

/**
 * Dữ liệu mẫu cho chạy thử local (profile {@code dev}). Chỉ tạo khi chưa có admin@test.local.
 * Mọi tài khoản dùng mật khẩu {@value #PASSWORD}.
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);
    static final String PASSWORD = "Passw0rd!";

    private final AccountService accounts;
    private final UserDirectory users;
    private final OrganizerCatalogService catalog;
    private final Clock clock;

    DevDataSeeder(AccountService accounts, UserDirectory users, OrganizerCatalogService catalog, Clock clock) {
        this.accounts = accounts;
        this.users = users;
        this.catalog = catalog;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByEmail("admin@test.local").isPresent()) {
            log.info("Dữ liệu mẫu đã có, bỏ qua");
            return;
        }
        accounts.createUser("admin@test.local", PASSWORD, "Quản trị viên", Role.ADMIN, null);
        UUID organizerId = accounts.createOrganizer("Sky Music Entertainment");
        var organizer = accounts.createUser("organizer1@test.local", PASSWORD, "Ban tổ chức Sky", Role.ORGANIZER,
                organizerId);
        accounts.createUser("staff1@test.local", PASSWORD, "Nhân viên soát vé 1", Role.STAFF, null);
        for (int i = 1; i <= 3; i++) {
            accounts.register("buyer" + i + "@test.local", PASSWORD, "Khách hàng " + i);
        }

        UUID orgId = catalog.organizerOf(organizer.getId());
        var venue = catalog.createVenue(orgId, "Sân vận động Mỹ Đình", "Lê Đức Thọ, Nam Từ Liêm", "Hà Nội");
        Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        var event = catalog.createEvent(orgId, new EventFields(venue.id(), "Sơn Tùng M-TP Live Concert 2026",
                "Đêm nhạc Sơn Tùng M-TP tại Mỹ Đình", now.plus(Duration.ofDays(40)),
                now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(39)), 4));
        var vip = catalog.addTicketType(orgId, event.id(), "VIP", TicketKind.SEATED, new BigDecimal("1500000"), null);
        catalog.addSeats(orgId, event.id(), vip.id(), "VIP",
                List.of(new SeatRow("A", 1, 20), new SeatRow("B", 1, 20), new SeatRow("C", 1, 20)));
        var ga = catalog.addTicketType(orgId, event.id(), "GA", TicketKind.GA, new BigDecimal("800000"), 1000);
        catalog.publish(orgId, event.id());
        catalog.assignStaff(orgId, event.id(), "staff1@test.local");

        log.info("""
                Đã tạo dữ liệu mẫu (mật khẩu {}):
                  admin@test.local, organizer1@test.local, staff1@test.local, buyer1..3@test.local
                  EVENT_ID={}  GA_TYPE_ID={}  VIP_TYPE_ID={}""", PASSWORD, event.id(), ga.id(), vip.id());
    }
}
