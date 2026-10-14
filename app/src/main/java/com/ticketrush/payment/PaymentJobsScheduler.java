package com.ticketrush.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty("ticketrush.jobs.enabled")
class PaymentJobsScheduler {

    private final RefundJob refundJob;

    PaymentJobsScheduler(RefundJob refundJob) {
        this.refundJob = refundJob;
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    void refund() {
        refundJob.run();
    }
}
