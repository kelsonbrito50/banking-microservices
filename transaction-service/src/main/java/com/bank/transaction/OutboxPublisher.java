package com.bank.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outbox.findTop100ByPublishedFalseOrderByIdAsc()) {
            try {
                // Block for the broker ack: only a confirmed send may mark the
                // event published, otherwise a broker outage would drop events.
                kafka.send(event.getTopic(), event.getPayload()).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("Kafka send failed, will retry event {} next run: {}", event.getId(), e.getMessage());
                return; // events acked earlier in this batch still commit as published
            }
            event.setPublished(true);
            log.info("Published outbox event {} to {}", event.getId(), event.getTopic());
        }
    }
}
