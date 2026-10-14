package com.ticketrush.ticketing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketrush.common.config.TicketRushProperties;
import com.ticketrush.common.error.BusinessException;
import com.ticketrush.common.error.ErrorCode;
import com.ticketrush.common.error.NotFoundException;
import com.ticketrush.common.error.ValidationException;
import com.ticketrush.common.events.TicketTransferred;
import com.ticketrush.common.events.Topics;
import com.ticketrush.common.security.Role;
import com.ticketrush.identity.UserDirectory;
import com.ticketrush.identity.UserDirectory.UserContact;
import com.ticketrush.outbox.OutboxWriter;

/**
 * Chuyển vé cho người khác. Đổi chủ và tăng {@code token_version} trong một câu lệnh có điều kiện,
 * nên QR cũ mất hiệu lực ngay. Đua với check-in: hai câu lệnh nhắm cùng một dòng nên chạy lần lượt,
 * bên đến sau thấy điều kiện của mình không còn đúng (vé USED, hoặc version đã đổi).
 */
@Service
public class TicketTransferService {

    private final TicketStore tickets;
    private final UserDirectory users;
    private final OutboxWriter outbox;
    private final TicketRushProperties properties;
    private final Clock clock;

    TicketTransferService(TicketStore tickets, UserDirectory users, OutboxWriter outbox,
            TicketRushProperties properties, Clock clock) {
        this.tickets = tickets;
        this.users = users;
        this.outbox = outbox;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TicketView transfer(UUID fromUserId, UUID ticketId, String recipientEmail) {
        Instant now = clock.instant();
        // Kiểm tra quyền sở hữu TRƯỚC khi tra email: người không có vé không dò được email nào đã đăng ký (ADR-009)
        TicketView ticket = tickets.view(ticketId)
                .filter(t -> t.ownerUserId().equals(fromUserId))
                .orElseThrow(() -> new NotFoundException("vé"));
        UserContact recipient = users.findByEmail(recipientEmail)
                .filter(u -> u.role() == Role.CUSTOMER)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND,
                        "Không có tài khoản khách hàng với email này"));
        if (recipient.id().equals(fromUserId)) {
            throw new ValidationException("Không thể chuyển vé cho chính mình");
        }
        if (!now.isBefore(ticket.eventStartsAt().minus(properties.ticket().transferCutoff()))) {
            throw notTransferable("Không chuyển nhượng được trong vòng "
                    + properties.ticket().transferCutoff().toHours() + " giờ trước giờ diễn");
        }

        Optional<Integer> newVersion = tickets.transfer(ticketId, fromUserId, recipient.id());
        if (newVersion.isEmpty()) {
            // 0 dòng: vé vừa đổi chủ (404) hoặc vừa được dùng/huỷ (409). Đọc lại để phân biệt.
            TicketView current = tickets.view(ticketId).orElseThrow(() -> new NotFoundException("vé"));
            if (!current.ownerUserId().equals(fromUserId)) {
                throw new NotFoundException("vé");
            }
            throw notTransferable("Vé đã được sử dụng hoặc đã huỷ");
        }
        tickets.insertTransfer(ticketId, fromUserId, recipient.id(), newVersion.get(), now);
        outbox.write("Ticket", ticketId, Topics.TICKET_TRANSFERRED, Topics.TICKET_EVENTS,
                new TicketTransferred(ticketId, ticket.eventId(), fromUserId, recipient.id(), newVersion.get(), now));
        return tickets.view(ticketId).orElseThrow();
    }

    private static BusinessException notTransferable(String detail) {
        return new BusinessException(ErrorCode.TICKET_NOT_TRANSFERABLE, detail);
    }
}
