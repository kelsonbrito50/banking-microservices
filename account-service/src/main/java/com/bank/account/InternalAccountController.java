package com.bank.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

/**
 * Service-to-service endpoints called by transaction-service. Deliberately
 * under /internal/** — the API gateway only routes /api/**, so clients can't
 * reach them through it. The service itself listens on 127.0.0.1 by default
 * (server.address), so other machines can't call it directly either. In Docker,
 * BIND_ADDRESS=0.0.0.0 opens it to the compose network only; no port is published.
 */
@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountController {

    private final AccountService service;
    private final AccountRepository accounts;

    public record MoveFundsRequest(@NotBlank String accountNumber,
                                   @NotNull @DecimalMin("0.01") BigDecimal amount) {}

    @GetMapping("/{accountNumber}")
    public Account get(@PathVariable String accountNumber) {
        return accounts.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @PostMapping("/debit")
    public ResponseEntity<Void> debit(@Valid @RequestBody MoveFundsRequest req) {
        service.debit(req.accountNumber(), req.amount());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/credit")
    public ResponseEntity<Void> credit(@Valid @RequestBody MoveFundsRequest req) {
        service.credit(req.accountNumber(), req.amount());
        return ResponseEntity.ok().build();
    }
}
