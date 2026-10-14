package com.ticketrush.payment;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ticketrush.common.config.TicketRushProperties;
import com.ticketrush.common.error.BusinessException;
import com.ticketrush.common.error.ErrorCode;
import com.ticketrush.payment.MockPayClient.CreateSessionRequest;
import com.ticketrush.payment.MockPayClient.CreateSessionResponse;
import com.ticketrush.payment.MockPayClient.MockPayException;

/**
 * {@code POST /orders/{id}/payments}. KHÔNG có {@code @Transactional}: transaction 1 tạo payment,
 * gọi MockPay ngoài transaction, transaction 2 lưu kết quả.
 *
 * <p>Ca khó: MockPay đã tạo phiên nhưng ta timeout và đánh dấu FAILED. Khách vẫn có thể trả trên phiên
 * đó; webhook {@code payment.succeeded} cho payment FAILED vẫn được nhận (FAILED → SUCCEEDED).
 */
@Service
public class PaymentInitiation {

    private static final Logger log = LoggerFactory.getLogger(PaymentInitiation.class);

    private final PaymentInitiationSteps steps;
    private final MockPayClient mockPay;
    private final TicketRushProperties properties;
    private final Clock clock;

    PaymentInitiation(PaymentInitiationSteps steps, MockPayClient mockPay, TicketRushProperties properties,
            Clock clock) {
        this.steps = steps;
        this.mockPay = mockPay;
        this.properties = properties;
        this.clock = clock;
    }

    public Initiated initiate(UUID userId, UUID orderId) {
        PaymentInitiationSteps.Prepared prepared = steps.prepare(userId, orderId);
        Payment payment = prepared.payment();
        if (!prepared.needsGatewayCall()) {
            return new Initiated(payment, prepared.created());
        }

        TicketRushProperties.MockPay config = properties.payment().mockpay();
        long expiresInSeconds = Math.max(1, Duration.between(clock.instant(), payment.getExpiresAt()).toSeconds());
        CreateSessionResponse session;
        try {
            // merchantRef = payment.id: gọi lại cùng merchantRef thì MockPay trả lại phiên cũ
            session = mockPay.createPayment(new CreateSessionRequest(payment.getId().toString(),
                    payment.getAmount(), payment.getCurrency(), "TicketRush order " + orderId,
                    config.returnUrl(), config.notifyUrl(), expiresInSeconds));
        } catch (MockPayException e) {
            log.warn("Tạo phiên MockPay thất bại cho payment {}: {}", payment.getId(), e.getMessage());
            steps.recordGatewayFailure(payment.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE,
                    "Cổng thanh toán không phản hồi, vui lòng thử lại");
        }
        Payment saved = steps.recordSession(payment.getId(), session.gatewayPaymentId(), session.paymentUrl());
        return new Initiated(saved, prepared.created());
    }

    public record Initiated(Payment payment, boolean created) {}
}
