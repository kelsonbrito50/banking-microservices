package com.bank.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService service;
    private final AccountRepository accounts;

    // Demo opening balance: there is no deposit flow, so new accounts may start
    // with up to 1000. Without these limits a caller could open an account with
    // any amount, or a negative balance.
    public record CreateRequest(@PositiveOrZero @DecimalMax("1000.00") BigDecimal initialDeposit) {}

    // Identity comes from the X-User-Id header the gateway sets after JWT
    // verification — never from the request body, so a caller can only create
    // and list accounts for themselves.
    @PostMapping
    public ResponseEntity<Account> create(@RequestHeader("X-User-Id") Long userId,
                                          @Valid @RequestBody CreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(userId, req.initialDeposit()));
    }

    @GetMapping
    public List<Account> myAccounts(@RequestHeader("X-User-Id") Long userId) {
        return accounts.findByUserId(userId);
    }
}
