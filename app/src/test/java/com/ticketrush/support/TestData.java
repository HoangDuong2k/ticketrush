package com.ticketrush.support;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import com.ticketrush.common.config.TicketRushProperties;
import com.ticketrush.common.security.Role;
import com.ticketrush.common.time.Times;
import com.ticketrush.ordering.CreateOrderRequest;
import com.ticketrush.ordering.OrderPlacement;
import com.ticketrush.payment.Payment;
import com.ticketrush.payment.PaymentInitiation;
import com.ticketrush.ticketing.QrTokenCodec;
import com.ticketrush.ticketing.TicketClaims;

import tools.jackson.databind.json.JsonMapper;

/**
 * Tạo dữ liệu test bằng JDBC (nhanh, không đi qua API). Mọi tài khoản dùng mật khẩu {@link #PASSWORD}.
 * Bean trong test, khai báo qua {@code @Import} ở {@link IntegrationTest}.
 */
public class TestData {

    public static final String PASSWORD = "Passw0rd!";

    private final JdbcTemplate jdbc;
    private final MutableClock clock;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final TicketRushProperties properties;
    private final OrderPlacement placement;
    private final PaymentInitiation paymentInitiation;
    private final JsonMapper json;
    private final QrTokenCodec qrCodec;
    private final AtomicLong sequence = new AtomicLong();
    private volatile String passwordHash;
    private volatile Api api;

    public TestData(JdbcTemplate jdbc, MutableClock clock, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            TicketRushProperties properties, OrderPlacement placement, PaymentInitiation paymentInitiation,
            JsonMapper json, QrTokenCodec qrCodec) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.placement = placement;
        this.paymentInitiation = paymentInitiation;
        this.json = json;
        this.qrCodec = qrCodec;
    }

    void useApi(Api api) {
        this.api = api;
    }

    // ---------- users ----------

    public UUID user() {
        return user(Role.CUSTOMER);
    }

    public UUID user(Role role) {
        return userWithEmail("u" + sequence.incrementAndGet() + "@test.local", role, null);
    }

    public UUID userWithEmail(String email, Role role, UUID organizerId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, email, password_hash, full_name, role, organizer_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, email, passwordHash(), "Người dùng " + email, role.name(), organizerId,
                Times.utc(clock.instant()));
        return id;
    }

    /** Insert {@code n} khách hàng bằng JDBC batch (đừng gọi API đăng ký 2.500 lần). */
    public List<UUID> users(int n) {
        List<UUID> ids = new ArrayList<>(n);
        List<Object[]> rows = new ArrayList<>(n);
        String hash = passwordHash();
        for (int i = 0; i < n; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            rows.add(new Object[] {id, "u" + sequence.incrementAndGet() + "@test.local", hash, "Khách " + i,
                    Role.CUSTOMER.name(), Times.utc(clock.instant())});
        }
        jdbc.batchUpdate("""
                INSERT INTO users (id, email, password_hash, full_name, role, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, rows);
        return ids;
    }

    public String emailOf(UUID userId) {
        return jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
    }

    /** JWT cho user, ký bằng đúng JwtEncoder của app (không cần gọi /auth/login). */
    public String token(UUID userId) {
        String role = jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofDays(1)))
                .claim("role", role)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** Một ban tổ chức kèm một tài khoản ORGANIZER. */
    public OrganizerRef organizer() {
        UUID organizerId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizers (id, name, created_at) VALUES (?, ?, ?)",
                organizerId, "BTC " + sequence.incrementAndGet(), Times.utc(clock.instant()));
        String email = "org" + sequence.incrementAndGet() + "@test.local";
        UUID userId = userWithEmail(email, Role.ORGANIZER, organizerId);
        return new OrganizerRef(organizerId, userId, email);
    }

    private String passwordHash() {
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);   // bcrypt chậm: băm một lần, dùng lại
        }
        return passwordHash;
    }

    // ---------- catalog ----------

    /** Sự kiện PUBLISHED đang mở bán theo {@code clock}. */
    public EventRef eventOnSale(Consumer<EventSpec> customizer) {
        EventSpec spec = new EventSpec(clock.instant());
        customizer.accept(spec);
        OrganizerRef organizer = organizer();
        UUID venueId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO venues (id, organizer_id, name, address, city, created_at)
                VALUES (?, ?, 'Nhà hát Lớn', '1 Tràng Tiền', 'Hà Nội', ?)
                """, venueId, organizer.organizerId(), Times.utc(clock.instant()));
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO events (id, organizer_id, venue_id, title, time_zone, starts_at, sale_opens_at,
                                    sale_closes_at, status, search_text, max_tickets_per_user, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Asia/Ho_Chi_Minh', ?, ?, ?, ?, ?, ?, ?, ?)
                """, eventId, organizer.organizerId(), venueId, spec.title, Times.utc(spec.startsAt),
                Times.utc(spec.saleOpensAt), Times.utc(spec.saleClosesAt), spec.status,
                spec.title.toLowerCase(), spec.maxTicketsPerUser, Times.utc(clock.instant()),
                Times.utc(clock.instant()));
        return new EventRef(eventId, organizer, venueId, spec.startsAt);
    }

    public UUID seatedTicketType(EventRef event, BigDecimal price) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, kind, price, created_at)
                VALUES (?, ?, ?, 'SEATED', ?, ?)
                """, id, event.id(), "VIP " + sequence.incrementAndGet(), price, Times.utc(clock.instant()));
        return id;
    }

    public UUID gaTicketType(EventRef event, BigDecimal price, int quantity) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ticket_types (id, event_id, name, kind, price, total_quantity, available_quantity, created_at)
                VALUES (?, ?, ?, 'GA', ?, ?, ?, ?)
                """, id, event.id(), "GA " + sequence.incrementAndGet(), price, quantity, quantity,
                Times.utc(clock.instant()));
        return id;
    }

    /** {@code count} ghế mới trên một hàng mới của sự kiện. */
    public List<UUID> seats(EventRef event, UUID ticketTypeId, int count) {
        String row = "R" + sequence.incrementAndGet();
        List<UUID> ids = new ArrayList<>(count);
        List<Object[]> rows = new ArrayList<>(count);
        for (int n = 1; n <= count; n++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            rows.add(new Object[] {id, event.id(), ticketTypeId, "S", row, n});
        }
        jdbc.batchUpdate("""
                INSERT INTO seats (id, event_id, ticket_type_id, section, row_label, seat_number)
                VALUES (?, ?, ?, ?, ?, ?)
                """, rows);
        return ids;
    }

    /** Nhân viên STAFF được phân công soát vé sự kiện. */
    public UUID assignedStaff(EventRef event) {
        UUID staff = user(Role.STAFF);
        jdbc.update("INSERT INTO event_staff (event_id, user_id) VALUES (?, ?)", event.id(), staff);
        return staff;
    }

    // ---------- orders, payments ----------

    /** Đơn PENDING_PAYMENT rỗng (không item) của {@code n} user khác nhau, dùng cho test giữ ghế/GA. */
    public List<UUID> pendingOrders(EventRef event, int n) {
        List<UUID> buyers = users(n);
        List<UUID> orders = new ArrayList<>(n);
        List<Object[]> rows = new ArrayList<>(n);
        Instant now = clock.instant();
        for (UUID buyer : buyers) {
            UUID id = UUID.randomUUID();
            orders.add(id);
            rows.add(new Object[] {id, buyer, event.id(), UUID.randomUUID().toString(), "0".repeat(64),
                    Times.utc(now.plus(Duration.ofMinutes(10))), Times.utc(now), Times.utc(now)});
        }
        jdbc.batchUpdate("""
                INSERT INTO orders (id, user_id, event_id, status, total_amount, currency, idempotency_key,
                                    request_hash, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING_PAYMENT', 0, 'VND', ?, ?, ?, ?, ?)
                """, rows);
        return orders;
    }

    public OrderPlacement.PlacementResult placeOrder(UUID buyer, CreateOrderRequest request) {
        return placement.place(buyer, UUID.randomUUID().toString(), request);
    }

    public Payment initiatePayment(UUID buyer, UUID orderId) {
        return paymentInitiation.initiate(buyer, orderId).payment();
    }

    /** Tạo đơn qua OrderPlacement, tạo payment, gửi webhook thành công đã ký. Trả orderId. */
    public UUID paidOrderViaWebhook(UUID buyer, CreateOrderRequest request) {
        UUID orderId = placeOrder(buyer, request).order().getId();
        Payment payment = initiatePayment(buyer, orderId);
        Api.Response response = sendWebhook(succeededWebhook(payment));
        if (response.status() != 200) {
            throw new IllegalStateException("Webhook thất bại: " + response.status() + " " + response.body());
        }
        return orderId;
    }

    /** Thanh toán đơn rồi chờ consumer phát vé (tối đa 10 giây). Trả id vé theo thứ tự phát. */
    public List<UUID> issuedTickets(UUID buyer, CreateOrderRequest request) {
        UUID orderId = paidOrderViaWebhook(buyer, request);
        int expected = request.items().stream()
                .mapToInt(item -> item.hasSeats() ? item.seatIds().size() : item.quantity()).sum();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM tickets WHERE order_id = ?", Integer.class, orderId) == expected);
        return jdbc.queryForList("SELECT id FROM tickets WHERE order_id = ? ORDER BY order_item_id, item_seq",
                UUID.class, orderId);
    }

    /** Token QR hiện tại của vé (theo token_version trong database). */
    public String qrToken(UUID ticketId) {
        return jdbc.queryForObject("SELECT event_id, token_version FROM tickets WHERE id = ?", (rs, i) ->
                qrCodec.encode(new TicketClaims(ticketId, rs.getObject("event_id", UUID.class),
                        rs.getInt("token_version"))), ticketId);
    }

    // ---------- webhook ----------

    public Map<String, Object> succeededWebhook(Payment payment) {
        return webhook("payment.succeeded", payment.getId(), payment.getGatewayPaymentId(),
                "tx_" + UUID.randomUUID().toString().replace("-", ""), payment.getAmount(), payment.getCurrency());
    }

    public Map<String, Object> failedWebhook(Payment payment) {
        return webhook("payment.failed", payment.getId(), payment.getGatewayPaymentId(), null, payment.getAmount(),
                payment.getCurrency());
    }

    public Map<String, Object> webhook(String type, UUID paymentId, String gatewayPaymentId,
            String gatewayTransactionId, BigDecimal amount, String currency) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "evt_" + UUID.randomUUID().toString().replace("-", ""));
        payload.put("type", type);
        payload.put("gatewayPaymentId", gatewayPaymentId);
        payload.put("gatewayTransactionId", gatewayTransactionId);
        payload.put("merchantRef", paymentId.toString());
        payload.put("amount", amount);
        payload.put("currency", currency);
        payload.put("occurredAt", clock.instant().toString());
        return payload;
    }

    /** POST webhook đã ký bằng secret của app, timestamp theo {@code clock}. */
    public Api.Response sendWebhook(Map<String, Object> payload) {
        return sendSignedWebhook(json.writeValueAsBytes(payload), properties.payment().mockpay().webhookSecret(),
                String.valueOf(payload.get("eventId")));
    }

    public Api.Response sendSignedWebhook(byte[] body, String secret, String eventId) {
        String timestamp = String.valueOf(clock.instant().getEpochSecond());
        return api.postRaw("/webhooks/payments/mockpay", body, Map.of(
                "X-MockPay-Event-Id", eventId,
                "X-MockPay-Timestamp", timestamp,
                "X-MockPay-Signature", sign(secret, timestamp, body)));
    }

    public static String sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- types ----------

    public record OrganizerRef(UUID organizerId, UUID userId, String email) {}

    public record EventRef(UUID id, OrganizerRef organizer, UUID venueId, Instant startsAt) {}

    public static final class EventSpec {
        String title = "Sự kiện test";
        Instant startsAt;
        Instant saleOpensAt;
        Instant saleClosesAt;
        String status = "PUBLISHED";
        int maxTicketsPerUser = 4;

        EventSpec(Instant now) {
            this.saleOpensAt = now.minus(Duration.ofHours(1));
            this.saleClosesAt = now.plus(Duration.ofDays(7));
            this.startsAt = now.plus(Duration.ofDays(10));
        }

        public EventSpec title(String title) {
            this.title = title;
            return this;
        }

        public EventSpec maxTicketsPerUser(int max) {
            this.maxTicketsPerUser = max;
            return this;
        }

        public EventSpec startsAt(Instant startsAt) {
            this.startsAt = startsAt;
            if (saleClosesAt.isAfter(startsAt)) {
                this.saleClosesAt = startsAt;
            }
            return this;
        }

        public EventSpec saleWindow(Instant opensAt, Instant closesAt) {
            this.saleOpensAt = opensAt;
            this.saleClosesAt = closesAt;
            return this;
        }

        public EventSpec draft() {
            this.status = "DRAFT";
            return this;
        }
    }
}
