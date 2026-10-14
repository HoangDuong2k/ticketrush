package com.ticketrush.mockpay.payment;

import com.ticketrush.mockpay.webhook.WebhookEvent;

/** Kết quả người mua chọn trên trang thanh toán (hoặc qua API admin). */
public enum PaymentResult {

    SUCCESS(PaymentStatus.SUCCEEDED, WebhookEvent.PAYMENT_SUCCEEDED),
    FAILED(PaymentStatus.FAILED, WebhookEvent.PAYMENT_FAILED),
    CANCELLED(PaymentStatus.CANCELLED, WebhookEvent.PAYMENT_FAILED);

    private final PaymentStatus status;
    private final String eventType;

    PaymentResult(PaymentStatus status, String eventType) {
        this.status = status;
        this.eventType = eventType;
    }

    public PaymentStatus status() {
        return status;
    }

    public String eventType() {
        return eventType;
    }
}
