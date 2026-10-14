package com.ticketrush.notification;

import java.util.List;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.ticketrush.common.events.EventMessages;
import com.ticketrush.common.events.TicketTransferred;
import com.ticketrush.common.events.TicketsIssued;
import com.ticketrush.common.events.Topics;
import com.ticketrush.identity.UserDirectory;
import com.ticketrush.identity.UserDirectory.UserContact;
import com.ticketrush.ticketing.TicketQueries;
import com.ticketrush.ticketing.TicketView;

/**
 * Consumer {@code ticketrush.ticket-events}: gửi email vé. Ưu tiên "gửi trùng còn hơn không gửi"
 * (ADR-007): sập sau khi gửi nhưng trước khi đánh dấu SENT thì message được xử lý lại và khách nhận
 * hai email. Gửi lỗi thì ném exception để error handler Kafka thử lại.
 */
@Component
class TicketNotifier {

    private static final Logger log = LoggerFactory.getLogger(TicketNotifier.class);

    private final EventMessages messages;
    private final NotificationDeliveries deliveries;
    private final TicketEmailSender sender;
    private final UserDirectory users;
    private final TicketQueries tickets;

    TicketNotifier(EventMessages messages, NotificationDeliveries deliveries, TicketEmailSender sender,
            UserDirectory users, TicketQueries tickets) {
        this.messages = messages;
        this.deliveries = deliveries;
        this.sender = sender;
        this.users = users;
        this.tickets = tickets;
    }

    @KafkaListener(topics = Topics.TICKET_EVENTS, groupId = "ticket-notifier")
    void onTicketEvent(ConsumerRecord<String, String> record) {
        switch (messages.eventType(record)) {
            case Topics.TICKETS_ISSUED -> ticketsIssued(messages.payload(record, TicketsIssued.class));
            case Topics.TICKET_TRANSFERRED -> ticketTransferred(messages.payload(record, TicketTransferred.class));
            default -> { }
        }
    }

    private void ticketsIssued(TicketsIssued event) {
        EventMessages.required(event.orderId(), "orderId");
        EventMessages.required(event.userId(), "userId");
        EventMessages.required(event.ticketIds(), "ticketIds");
        Optional<UserContact> buyer = users.findContact(event.userId());
        if (buyer.isEmpty()) {
            log.warn("Bỏ qua TicketsIssued của đơn {}: không tìm thấy người mua", event.orderId());
            return;
        }
        // Chỉ gửi vé người mua còn đang giữ (vé đã chuyển nhượng thì QR cũ không còn dùng được)
        List<TicketView> owned = tickets.views(event.ticketIds()).stream()
                .filter(t -> t.ownerUserId().equals(event.userId()))
                .toList();
        String dedupeKey = "tickets-issued:" + event.orderId();
        deliver(dedupeKey, buyer.get().email(), () -> sender.sendTickets(buyer.get().email(), buyer.get().fullName(),
                "Vé của bạn: " + (owned.isEmpty() ? "TicketRush" : owned.getFirst().eventTitle()),
                "Thanh toán thành công. Dưới đây là " + owned.size() + " vé của bạn.", owned));
    }

    private void ticketTransferred(TicketTransferred event) {
        EventMessages.required(event.ticketId(), "ticketId");
        EventMessages.required(event.fromUserId(), "fromUserId");
        EventMessages.required(event.toUserId(), "toUserId");
        Optional<TicketView> ticket = tickets.view(event.ticketId());
        Optional<UserContact> from = users.findContact(event.fromUserId());
        Optional<UserContact> to = users.findContact(event.toUserId());
        if (ticket.isEmpty() || from.isEmpty() || to.isEmpty()) {
            log.warn("Bỏ qua TicketTransferred của vé {}: thiếu dữ liệu", event.ticketId());
            return;
        }
        String base = "ticket-transferred:" + event.ticketId() + ":v" + event.tokenVersion();
        // Người nhận chỉ nhận QR nếu vé vẫn thuộc họ với đúng version này (chưa chuyển tiếp lần nữa)
        List<TicketView> forRecipient = ticket.filter(t -> t.ownerUserId().equals(event.toUserId())
                && t.tokenVersion() == event.tokenVersion()).stream().toList();
        deliver(base + ":to", to.get().email(), () -> sender.sendTickets(to.get().email(), to.get().fullName(),
                "Bạn nhận được vé: " + ticket.get().eventTitle(),
                from.get().fullName() + " đã chuyển vé cho bạn.", forRecipient));
        deliver(base + ":from", from.get().email(), () -> sender.sendNotice(from.get().email(), from.get().fullName(),
                "Bạn đã chuyển vé: " + ticket.get().eventTitle(),
                "Vé " + ticket.get().ticketTypeName() + " đã được chuyển cho " + to.get().email()
                        + ". Mã QR cũ của vé này không còn sử dụng được."));
    }

    private void deliver(String dedupeKey, String recipient, Runnable send) {
        if (deliveries.alreadySent(dedupeKey, recipient)) {
            return;
        }
        try {
            send.run();
        } catch (RuntimeException e) {
            deliveries.recordFailure(dedupeKey);
            throw e;
        }
        deliveries.markSent(dedupeKey);
    }
}
