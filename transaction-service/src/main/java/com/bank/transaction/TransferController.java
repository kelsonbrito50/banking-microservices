package com.bank.transaction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService service;

    public record TransferRequest(@NotBlank String fromAccount,
                                  @NotBlank String toAccount,
                                  @NotNull @DecimalMin("0.01") BigDecimal amount) {}

    // X-User-Id is set by the gateway after JWT verification — the service uses
    // it to enforce that callers only move money out of their own accounts.
    @PostMapping
    public TransferTransaction transfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @RequestHeader("X-User-Id") Long userId,
                                        @Valid @RequestBody TransferRequest req) {
        return service.transfer(idempotencyKey, userId, req.fromAccount(), req.toAccount(), req.amount());
    }

    @GetMapping("/history/{accountNumber}")
    public List<TransferTransaction> history(@PathVariable String accountNumber,
                                             @RequestHeader("X-User-Id") Long userId) {
        return service.history(accountNumber, userId);
    }
}
