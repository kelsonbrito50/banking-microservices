package com.bank.transaction;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = @Index(columnList = "idempotencyKey", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    private String fromAccount;
    private String toAccount;

    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String failureReason;
    private Instant createdAt;

    public enum Status { PENDING, COMPLETED, FAILED, COMPENSATION_REQUIRED }
}
