package com.bank.account;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean isValid(String amount) {
        var deposit = amount == null ? null : new BigDecimal(amount);
        return validator.validate(new AccountController.CreateRequest(deposit)).isEmpty();
    }

    @Test
    void acceptsMissingZeroAndCappedDeposits() {
        assertThat(isValid(null)).isTrue();
        assertThat(isValid("0")).isTrue();
        assertThat(isValid("1000.00")).isTrue();
    }

    @Test
    void rejectsNegativeAndOversizedDeposits() {
        assertThat(isValid("-500")).isFalse();
        assertThat(isValid("1000.01")).isFalse();
        assertThat(isValid("1000000")).isFalse();
    }
}
