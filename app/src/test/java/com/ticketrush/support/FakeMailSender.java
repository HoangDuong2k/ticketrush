package com.ticketrush.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Thay JavaMailSender trong test: ghi lại email đã gửi, giả lập mail server lỗi N lần đầu. */
public class FakeMailSender extends JavaMailSenderImpl {

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            attempts.incrementAndGet();
            if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new MailSendException("Giả lập mail server lỗi");
            }
            try {
                message.saveChanges();   // như JavaMailSenderImpl thật: điền header Content-Type từ nội dung
            } catch (MessagingException e) {
                throw new MailSendException("Không lưu được email", e);
            }
            sent.add(message);
        }
    }

    public List<MimeMessage> sent() {
        return sent;
    }

    public int attempts() {
        return attempts.get();
    }

    public void failNext(int times) {
        failuresRemaining.set(times);
    }

    public void reset() {
        sent.clear();
        failuresRemaining.set(0);
        attempts.set(0);
    }
}
