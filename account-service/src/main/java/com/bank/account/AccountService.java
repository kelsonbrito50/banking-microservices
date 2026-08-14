package com.bank.account;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accounts;
    private final StringRedisTemplate redis;

    @Transactional
    public Account create(Long userId, BigDecimal initialDeposit) {
        return accounts.save(Account.builder()
                .accountNumber(UUID.randomUUID().toString().substring(0, 12))
                .userId(userId)
                .balance(initialDeposit == null ? BigDecimal.ZERO : initialDeposit)
                .build());
    }

    @Transactional
    public void debit(String accountNumber, BigDecimal amount) {
        Account acc = lockedAccount(accountNumber);
        if (acc.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        }
        acc.setBalance(acc.getBalance().subtract(amount));
        evictCache(accountNumber);
    }

    @Transactional
    public void credit(String accountNumber, BigDecimal amount) {
        Account acc = lockedAccount(accountNumber);
        acc.setBalance(acc.getBalance().add(amount));
        evictCache(accountNumber);
    }

    private Account lockedAccount(String accountNumber) {
        return accounts.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private void evictCache(String accountNumber) {
        redis.delete("balance:" + accountNumber);
    }
}
