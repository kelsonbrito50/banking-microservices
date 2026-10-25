package com.bank.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TransferService {

    private final TransactionRepository transactions;
    private final OutboxRepository outbox;
    private final ObjectMapper mapper;
    private final TransactionTemplate txTemplate;
    private final RestClient accountClient;

    public TransferService(TransactionRepository transactions,
                           OutboxRepository outbox,
                           ObjectMapper mapper,
                           PlatformTransactionManager txManager,
                           RestClient.Builder restClientBuilder,
                           @Value("${services.account-url}") String accountServiceUrl) {
        this.transactions = transactions;
        this.outbox = outbox;
        this.mapper = mapper;
        this.txTemplate = new TransactionTemplate(txManager);
        // The auto-configured builder carries the spring.http.client timeouts
        // from application.yml; RestClient.create() would have none.
        this.accountClient = restClientBuilder.baseUrl(accountServiceUrl).build();
    }

    record AccountInfo(String accountNumber, Long userId) {}

    /**
     * Orchestrated SAGA:
     *  1. Idempotency check — same key never executes twice
     *  2. Ownership check — caller must own the source account
     *  3. Save PENDING transaction (own DB tx, committed before any remote call)
     *  4. Debit source account
     *  5. Credit destination account
     *  6a. Success  -> COMPLETED + outbox event transfer.completed
     *  6b. Credit rejected (4xx) -> compensating credit back to source, FAILED + transfer.failed
     *  6c. Compensation itself fails -> COMPENSATION_REQUIRED + ops alert event
     *
     * Only a 4xx is a definite "no": account-service rejected the call and its
     * transaction rolled back. A 5xx or a timeout means the outcome is unknown,
     * because the debit or credit may have committed before the error. Then the
     * transfer is marked COMPENSATION_REQUIRED for a person to check. Refunding
     * on a guess could create money; ignoring it could lose money.
     *
     * The REST calls run OUTSIDE any database transaction: each state change
     * commits in its own short transaction, so the record, its idempotency key
     * and the outbox event survive even when the transfer fails.
     */
    public TransferTransaction transfer(String idempotencyKey, Long userId,
                                        String from, String to, BigDecimal amount) {

        // 1. Idempotency: return the previous result instead of re-executing
        var existing = transactions.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();

        // 2. Authorization: caller must own the source; destination must exist
        requireOwnership(from, userId);
        fetchAccount(to);

        // 3. PENDING record. A concurrent request with the same key loses the
        //    unique-constraint race and is handed the winner's record.
        TransferTransaction tx;
        try {
            tx = txTemplate.execute(s -> transactions.save(TransferTransaction.builder()
                    .idempotencyKey(idempotencyKey)
                    .fromAccount(from).toAccount(to).amount(amount)
                    .status(TransferTransaction.Status.PENDING)
                    .createdAt(Instant.now())
                    .build()));
        } catch (DataIntegrityViolationException e) {
            return transactions.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Concurrent request with same idempotency key"));
        }

        // 4. Debit
        try {
            moveFunds("/internal/accounts/debit", from, amount);
        } catch (HttpClientErrorException e) {
            return fail(tx, TransferTransaction.Status.FAILED, "transfer.failed",
                    "Debit failed: " + e.getStatusCode(), HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (Exception e) {
            return outcomeUnknown(tx, "Debit", e);
        }

        // 5. Credit — refund the source only when the credit was clearly rejected
        try {
            moveFunds("/internal/accounts/credit", to, amount);
        } catch (HttpClientErrorException e) {
            log.warn("Credit rejected ({}), compensating debit on {}", e.getStatusCode(), from);
            try {
                moveFunds("/internal/accounts/credit", from, amount);
            } catch (Exception compensationError) {
                log.error("COMPENSATION FAILED for tx {}: {} was debited but not refunded",
                        tx.getId(), from, compensationError);
                return fail(tx, TransferTransaction.Status.COMPENSATION_REQUIRED,
                        "transfer.compensation-required",
                        "Credit and compensation both failed — manual intervention required",
                        HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return fail(tx, TransferTransaction.Status.FAILED, "transfer.failed",
                    "Credit failed: " + e.getStatusCode(), HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (Exception e) {
            return outcomeUnknown(tx, "Credit", e);
        }

        // 6a. Success
        return txTemplate.execute(s -> {
            tx.setStatus(TransferTransaction.Status.COMPLETED);
            TransferTransaction saved = transactions.save(tx);
            saveOutbox("transfer.completed", saved);
            return saved;
        });
    }

    public List<TransferTransaction> history(String accountNumber, Long userId) {
        requireOwnership(accountNumber, userId);
        return transactions.findTop50ByFromAccountOrToAccountOrderByCreatedAtDesc(accountNumber, accountNumber);
    }

    private void moveFunds(String path, String accountNumber, BigDecimal amount) {
        accountClient.post().uri(path)
                .body(Map.of("accountNumber", accountNumber, "amount", amount))
                .retrieve().toBodilessEntity();
    }

    private AccountInfo fetchAccount(String accountNumber) {
        try {
            return accountClient.get().uri("/internal/accounts/{n}", accountNumber)
                    .retrieve().body(AccountInfo.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found: " + accountNumber);
        }
    }

    private void requireOwnership(String accountNumber, Long userId) {
        if (!fetchAccount(accountNumber).userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account does not belong to caller");
        }
    }

    /** 5xx or timeout: the step may or may not have happened, so flag it instead of guessing. */
    private TransferTransaction outcomeUnknown(TransferTransaction tx, String step, Exception e) {
        log.error("{} outcome unknown for tx {}: manual check required", step, tx.getId(), e);
        return fail(tx, TransferTransaction.Status.COMPENSATION_REQUIRED,
                "transfer.compensation-required",
                step + " outcome unknown — manual check required",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /** Persists the terminal status + outbox event in their own transaction, THEN throws. */
    private TransferTransaction fail(TransferTransaction tx, TransferTransaction.Status status,
                                     String topic, String reason, HttpStatus httpStatus) {
        txTemplate.executeWithoutResult(s -> {
            tx.setStatus(status);
            tx.setFailureReason(reason);
            transactions.save(tx);
            saveOutbox(topic, tx);
        });
        throw new ResponseStatusException(httpStatus, reason);
    }

    @SneakyThrows
    private void saveOutbox(String topic, TransferTransaction tx) {
        outbox.save(OutboxEvent.builder()
                .topic(topic)
                .payload(mapper.writeValueAsString(tx))
                .published(false)
                .createdAt(Instant.now())
                .build());
    }
}
