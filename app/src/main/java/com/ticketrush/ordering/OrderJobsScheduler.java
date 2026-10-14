package com.ticketrush.ordering;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Chỉ chạy khi {@code ticketrush.jobs.enabled=true}. Test tắt đi và gọi {@link OrderExpiryJob#sweep()} trực tiếp. */
@Component
@ConditionalOnBooleanProperty("ticketrush.jobs.enabled")
class OrderJobsScheduler {

    private final OrderExpiryJob expiryJob;

    OrderJobsScheduler(OrderExpiryJob expiryJob) {
        this.expiryJob = expiryJob;
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    void sweepExpiredOrders() {
        expiryJob.sweep();
    }
}
