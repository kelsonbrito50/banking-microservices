package com.bank.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * The saga's failure paths. Rule under test: refund only when account-service
 * clearly rejected the call (4xx). When the outcome is unknown (5xx, timeout)
 * the transfer is flagged COMPENSATION_REQUIRED instead of guessing.
 */
class TransferServiceTest {

    private static final String BASE = "http://account-service";
    private static final String FROM = "aaa111";
    private static final String TO = "bbb222";
    private static final long USER = 7L;

    private TransactionRepository transactions;
    private OutboxRepository outbox;
    private MockRestServiceServer accountService;
    private TransferService service;

    @BeforeEach
    void setUp() {
        transactions = mock(TransactionRepository.class);
        outbox = mock(OutboxRepository.class);
        when(transactions.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        RestClient.Builder builder = RestClient.builder();
        accountService = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();

        service = new TransferService(transactions, outbox,
                new ObjectMapper().findAndRegisterModules(), txManager, builder, BASE);

        expectAccount(FROM, USER);
        expectAccount(TO, 99L);
    }

    @Test
    void completesWhenDebitAndCreditSucceed() {
        expectMove("debit", FROM).andRespond(withSuccess());
        expectMove("credit", TO).andRespond(withSuccess());

        TransferTransaction tx = transfer();

        assertThat(tx.getStatus()).isEqualTo(TransferTransaction.Status.COMPLETED);
        assertThat(lastOutboxTopic()).isEqualTo("transfer.completed");
        accountService.verify();
    }

    @Test
    void debitRejectedFailsWithoutMovingMoney() {
        expectMove("debit", FROM).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(lastSavedStatus()).isEqualTo(TransferTransaction.Status.FAILED);
        assertThat(lastOutboxTopic()).isEqualTo("transfer.failed");
        accountService.verify();   // no credit call was expected, so none was made
    }

    @Test
    void debitServerErrorIsFlaggedNotLeftPending() {
        expectMove("debit", FROM).andRespond(withServerError());

        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(lastSavedStatus()).isEqualTo(TransferTransaction.Status.COMPENSATION_REQUIRED);
        assertThat(lastOutboxTopic()).isEqualTo("transfer.compensation-required");
        accountService.verify();
    }

    @Test
    void creditTimeoutIsFlaggedWithoutRefund() {
        expectMove("debit", FROM).andRespond(withSuccess());
        expectMove("credit", TO).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(lastSavedStatus()).isEqualTo(TransferTransaction.Status.COMPENSATION_REQUIRED);
        assertThat(lastOutboxTopic()).isEqualTo("transfer.compensation-required");
        accountService.verify();   // a refund credit to FROM would fail as an unexpected request
    }

    @Test
    void creditRejectedRefundsTheSource() {
        expectMove("debit", FROM).andRespond(withSuccess());
        expectMove("credit", TO).andRespond(withStatus(HttpStatus.NOT_FOUND));
        expectMove("credit", FROM).andRespond(withSuccess());

        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(lastSavedStatus()).isEqualTo(TransferTransaction.Status.FAILED);
        assertThat(lastOutboxTopic()).isEqualTo("transfer.failed");
        accountService.verify();
    }

    private TransferTransaction transfer() {
        return service.transfer("key-1", USER, FROM, TO, new BigDecimal("250.00"));
    }

    private void assertStatus(HttpStatus expected) {
        assertThatThrownBy(this::transfer)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private void expectAccount(String number, long owner) {
        accountService.expect(requestTo(BASE + "/internal/accounts/" + number))
                .andRespond(withSuccess("{\"accountNumber\":\"" + number + "\",\"userId\":" + owner + "}",
                        MediaType.APPLICATION_JSON));
    }

    private org.springframework.test.web.client.ResponseActions expectMove(String op, String account) {
        return accountService.expect(requestTo(BASE + "/internal/accounts/" + op))
                .andExpect(jsonPath("$.accountNumber").value(account));
    }

    private TransferTransaction.Status lastSavedStatus() {
        ArgumentCaptor<TransferTransaction> saved = ArgumentCaptor.forClass(TransferTransaction.class);
        verify(transactions, atLeastOnce()).save(saved.capture());
        return saved.getValue().getStatus();
    }

    private String lastOutboxTopic() {
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox, atLeastOnce()).save(event.capture());
        return event.getValue().getTopic();
    }
}
