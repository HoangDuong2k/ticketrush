package com.ticketrush.outbox;

import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;

import org.springframework.util.backoff.ExponentialBackOff;

import com.ticketrush.common.events.InvalidEventPayloadException;
import com.ticketrush.common.events.Topics;

@Configuration(proxyBeanMethods = false)
class KafkaConfig {

    /** Topic DLT cùng số partition với topic gốc: recoverer mặc định gửi vào partition cùng số. */
    private static final int PARTITIONS = 3;
    static final String DLT_SUFFIX = ".DLT";

    @Bean
    KafkaAdmin.NewTopics ticketRushTopics() {
        return new KafkaAdmin.NewTopics(
                topic(Topics.ORDER_EVENTS), topic(Topics.ORDER_EVENTS + DLT_SUFFIX),
                topic(Topics.TICKET_EVENTS), topic(Topics.TICKET_EVENTS + DLT_SUFFIX));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(1).build();
    }

    /**
     * Lỗi trong listener: thử lại có backoff (0,5s → 1s → 2s … tối đa 30s mỗi lần) trong tối đa 10 phút,
     * rồi đẩy sang {@code <topic>.DLT}. Đủ lâu để qua một lần DB failover hay mail server khởi động lại.
     * Message hỏng thì thử lại cũng vô ích, đẩy thẳng sang DLT. Đơn PAID mà message đã vào DLT vẫn được
     * {@code PaidOrderSweeper} phát vé sau. Spring Boot tự gắn bean CommonErrorHandler này vào listener container.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<?, ?> template) {
        var backOff = new ExponentialBackOff(500, 2.0);
        backOff.setMaxInterval(30_000);
        backOff.setMaxElapsedTime(Duration.ofMinutes(10).toMillis());

        // Ghi rõ tên topic: Spring Kafka 4 đổi hậu tố mặc định thành "-dlt"; ta giữ "<topic>.DLT", cùng partition
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, e) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidEventPayloadException.class);
        return handler;
    }
}
