package com.bank.transaction;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Outbox pattern: the event is saved in the SAME database transaction as the
 * business change. A scheduled publisher relays it to Kafka afterwards, so no
 * event is ever lost even if Kafka is down at commit time.
 */
@Entity
@Table(name = "outbox_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String topic;

    @Column(columnDefinition = "text")
    private String payload;

    private boolean published;
    private Instant createdAt;
}
