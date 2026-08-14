package com.bank.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface TransactionRepository extends JpaRepository<TransferTransaction, Long> {
    Optional<TransferTransaction> findByIdempotencyKey(String key);
    List<TransferTransaction> findTop50ByFromAccountOrToAccountOrderByCreatedAtDesc(String from, String to);
}

interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100ByPublishedFalseOrderByIdAsc();
}
