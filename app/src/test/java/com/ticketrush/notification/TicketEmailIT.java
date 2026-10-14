package com.ticketrush.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import com.ticketrush.common.events.Topics;
import com.ticketrush.ordering.CreateOrderRequest;
import com.ticketrush.ordering.CreateOrderRequest.Item;
import com.ticketrush.support.IntegrationTest;
import com.ticketrush.support.KafkaTestMessages;

/** Bước 17: email vé. JavaMailSender được thay bằng FakeMailSender ghi lại email. */
class TicketEmailIT extends IntegrationTest {

    @Autowired KafkaTemplate<String, String> kafka;

    @Test
    void paying_sends_one_email_to_the_buyer_with_one_qr_per_ticket() throws Exception {
        Paid p = paid();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));

        MimeMessage message = mail.sent().getFirst();
        assertThat(message.getAllRecipients()[0]).hasToString(testData.emailOf(p.buyer));
        assertThat(message.getSubject()).contains("Vé của bạn");
        assertThat(inlineImages(message)).isEqualTo(3);
        assertThat(htmlOf(message)).contains("Không chia sẻ mã QR");
    }

    @Test
    void tickets_issued_delivered_twice_still_sends_one_email() {
        Paid p = paid();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE event_type = 'TicketsIssued'", String.class);

        KafkaTestMessages.send(kafka, Topics.TICKET_EVENTS, p.orderId.toString(), Topics.TICKETS_ISSUED, payload);

        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(mail.sent()).hasSize(1));
        assertThat(jdbc.queryForObject("SELECT status FROM notification_deliveries", String.class)).isEqualTo("SENT");
    }

    @Test
    void mail_server_failing_twice_is_retried_and_the_email_still_goes_out() {
        mail.failNext(2);

        paid();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        assertThat(mail.attempts()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT attempts FROM notification_deliveries", Integer.class)).isEqualTo(3);
    }

    @Test
    void transfer_emails_both_people_and_the_recipient_gets_the_new_qr() {
        Paid p = paid();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(1));
        UUID ticket = jdbc.queryForList("SELECT id FROM tickets WHERE order_id = ?", UUID.class, p.orderId).getFirst();
        UUID friend = testData.user();

        api.post("/tickets/" + ticket + "/transfer", testData.token(p.buyer),
                java.util.Map.of("recipientEmail", testData.emailOf(friend)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(mail.sent()).hasSize(3));
        List<String> recipients = mail.sent().stream().skip(1).map(m -> {
            try {
                return m.getAllRecipients()[0].toString();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).toList();
        assertThat(recipients).containsExactlyInAnyOrder(testData.emailOf(friend), testData.emailOf(p.buyer));
    }

    private Paid paid() {
        var event = testData.eventOnSale(e -> {});
        UUID vip = testData.seatedTicketType(event, new BigDecimal("1500000"));
        List<UUID> seats = testData.seats(event, vip, 1);
        UUID ga = testData.gaTicketType(event, new BigDecimal("800000"), 10);
        UUID buyer = testData.user();
        UUID orderId = testData.paidOrderViaWebhook(buyer,
                CreateOrderRequest.of(event.id(), Item.seats(vip, seats), Item.ga(ga, 2)));
        return new Paid(orderId, buyer);
    }

    private static int inlineImages(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            int count = 0;
            for (int i = 0; i < multipart.getCount(); i++) {
                count += inlineImages(multipart.getBodyPart(i));
            }
            return count;
        }
        return part.isMimeType("image/png") && part instanceof BodyPart body && body.getHeader("Content-ID") != null
                ? 1 : 0;
    }

    private static String htmlOf(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            StringBuilder html = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) {
                html.append(htmlOf(multipart.getBodyPart(i)));
            }
            return html.toString();
        }
        return "";
    }

    record Paid(UUID orderId, UUID buyer) {}
}
