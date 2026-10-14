package com.ticketrush.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.kafka.core.KafkaTemplate;

import static org.awaitility.Awaitility.await;

/** Gửi tay và đọc message Kafka trong test. */
public final class KafkaTestMessages {

    public static void send(KafkaTemplate<String, String> kafka, String topic, String key, String eventType,
            String payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, payload);
        if (eventType != null) {
            record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        }
        try {
            kafka.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<ConsumerRecord<String, String>> consume(KafkaConnectionDetails kafka, String topic,
            int expected, Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers()));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            await().atMost(timeout).until(() -> {
                consumer.poll(Duration.ofMillis(200)).forEach(received::add);
                return received.size() >= expected;
            });
        }
        return received;
    }

    private KafkaTestMessages() {}
}
