package com.ticketrush.notification;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.ticketrush.common.config.TicketRushProperties;
import com.ticketrush.ticketing.TicketQueries;
import com.ticketrush.ticketing.TicketStatus;
import com.ticketrush.ticketing.TicketView;

/** Soạn và gửi email có QR từng vé (ảnh inline). Không ghi token QR vào log. */
@Component
class TicketEmailSender {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy", Locale.forLanguageTag("vi-VN"));

    private final JavaMailSender mail;
    private final TicketQueries tickets;
    private final TicketRushProperties properties;

    TicketEmailSender(JavaMailSender mail, TicketQueries tickets, TicketRushProperties properties) {
        this.mail = mail;
        this.tickets = tickets;
        this.properties = properties;
    }

    /** Email chứa QR của các vé còn hiệu lực trong {@code ticketList}. */
    void sendTickets(String to, String fullName, String subject, String intro, List<TicketView> ticketList) {
        List<TicketView> valid = ticketList.stream().filter(t -> t.status() == TicketStatus.VALID).toList();
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.notification().from());
            helper.setTo(to);
            helper.setSubject(subject);

            StringBuilder html = new StringBuilder()
                    .append("<p>Xin chào ").append(HtmlUtils.htmlEscape(fullName)).append(",</p>")
                    .append("<p>").append(HtmlUtils.htmlEscape(intro)).append("</p>");
            for (int i = 0; i < valid.size(); i++) {
                TicketView t = valid.get(i);
                html.append("<hr><p><b>").append(HtmlUtils.htmlEscape(t.eventTitle())).append("</b><br>")
                        .append(WHEN.format(t.eventStartsAt().atZone(t.eventTimeZone()))).append("<br>")
                        .append("Loại vé: ").append(HtmlUtils.htmlEscape(t.ticketTypeName()));
                if (t.seatLabel() != null) {
                    html.append(" · Ghế: ").append(HtmlUtils.htmlEscape(t.seatLabel()));
                }
                html.append("<br><a href=\"").append(properties.notification().publicBaseUrl())
                        .append("/tickets/").append(t.ticketId()).append("\">Xem vé</a></p>")
                        .append("<img src=\"cid:qr-").append(i).append("\" width=\"240\" height=\"240\" alt=\"QR\">");
            }
            html.append("<p><b>Không chia sẻ mã QR.</b> Ai cầm mã QR cũng vào cửa được, và mỗi vé chỉ vào được một lần.</p>");
            helper.setText(html.toString(), true);

            for (int i = 0; i < valid.size(); i++) {
                helper.addInline("qr-" + i, new ByteArrayResource(tickets.qrPng(valid.get(i))), "image/png");
            }
            mail.send(message);
        } catch (MessagingException e) {
            throw new MailSendException("Không soạn được email vé", e);
        }
    }

    void sendNotice(String to, String fullName, String subject, String text) {
        try {
            MimeMessage message = mail.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.notification().from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText("Xin chào " + fullName + ",\n\n" + text + "\n\nTicketRush");
            mail.send(message);
        } catch (MessagingException e) {
            throw new MailSendException("Không soạn được email thông báo", e);
        }
    }
}
