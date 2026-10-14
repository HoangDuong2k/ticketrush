package com.ticketrush.mockpay.config;

import java.time.Clock;
import java.util.concurrent.ThreadLocalRandom;

import com.ticketrush.mockpay.chaos.ChaosRandom;
import com.ticketrush.mockpay.webhook.WebhookSigner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MockPayConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ChaosRandom chaosRandom() {
        return () -> ThreadLocalRandom.current().nextDouble();
    }

    @Bean
    WebhookSigner webhookSigner(MockPayProperties properties) {
        return new WebhookSigner(properties.webhookSecret());
    }
}
