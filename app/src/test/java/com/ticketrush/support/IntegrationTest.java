package com.ticketrush.support;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.json.JsonMapper;

/**
 * Lớp cha cho integration test. KHÔNG đặt {@code @Transactional}: nếu có, mọi thao tác chạy trong
 * transaction của luồng test và bị rollback cuối test, các luồng khác không thấy dữ liệu, nên test
 * đồng thời vô nghĩa. Vì vậy dọn dữ liệu bằng TRUNCATE trước mỗi test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfig.class, TestData.class})
@ActiveProfiles("test")
public abstract class IntegrationTest {

    public static final Instant START = Instant.parse("2026-11-01T12:00:00Z");

    private static final String TRUNCATE = """
            TRUNCATE checkin_logs, ticket_transfers, tickets, notification_deliveries, outbox_events,
                     payment_webhook_logs, payments, order_items, purchase_quotas, orders, event_staff,
                     seats, ticket_types, events, venues, users, organizers
            RESTART IDENTITY CASCADE
            """;

    @LocalServerPort
    protected int port;

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected MutableClock clock;
    @Autowired protected TestData testData;
    @Autowired protected FakeMailSender mail;
    @Autowired protected JsonMapper json;
    @Autowired private KafkaListenerEndpointRegistry kafkaListeners;

    protected final FakeGateway gateway = FakeGateway.INSTANCE;
    protected Api api;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("ticketrush.payment.mockpay.base-url", FakeGateway.INSTANCE::baseUrl);
        registry.add("ticketrush.qr.active-key-id", () -> "k1");
        registry.add("ticketrush.qr.private-key", () -> TestKeys.privateBase64(TestKeys.QR_K1));
        registry.add("ticketrush.qr.public-keys.k1", () -> TestKeys.publicBase64(TestKeys.QR_K1));
    }

    @BeforeEach
    void resetState() {
        truncateAll();
        clock.set(START);
        gateway.reset();
        mail.reset();
        api = new Api(port, json);
        testData.useApi(api);
        // Chờ consumer Kafka nhận partition, để message đầu tiên của test không phải chờ rebalance
        for (MessageListenerContainer container : kafkaListeners.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 3);
        }
    }

    /** Consumer Kafka của test trước có thể đang giữ khoá; TRUNCATE hiếm khi bị chọn làm nạn nhân deadlock. */
    private void truncateAll() {
        for (int attempt = 1; ; attempt++) {
            try {
                jdbc.execute(TRUNCATE);
                return;
            } catch (DataAccessException e) {
                if (attempt == 3) {
                    throw e;
                }
            }
        }
    }
}
