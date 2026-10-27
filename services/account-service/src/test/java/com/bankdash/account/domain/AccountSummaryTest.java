package com.bankdash.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.account.domain.AccountSummary.Status;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountSummaryTest {

    static final UUID USER = UUID.randomUUID();

    static Account account(AccountType type, String balance) {
        return new Account(UUID.randomUUID(), USER, type, Account.CURRENCY, new BigDecimal(balance), Instant.now());
    }

    @Test
    void ac1_readyWithCheckingAsMyBalanceAndSavingsAsTotalSaving() {
        // Savings listed first: the order of the rows must not matter.
        var summary = AccountSummary.of(List.of(account(AccountType.SAVINGS, "300.00"), account(AccountType.CHECKING, "1250.50")));

        assertThat(summary.status()).isEqualTo(Status.READY);
        assertThat(summary.myBalance()).isEqualTo(new BigDecimal("1250.50"));
        assertThat(summary.totalSaving()).isEqualTo(new BigDecimal("300.00"));
    }

    @Test
    void ac3_zeroBalancesAreReady() {
        var summary = AccountSummary.of(List.of(account(AccountType.CHECKING, "0.00"), account(AccountType.SAVINGS, "0.00")));

        assertThat(summary.status()).isEqualTo(Status.READY);
        assertThat(summary.myBalance()).isEqualByComparingTo("0");
        assertThat(summary.totalSaving()).isEqualByComparingTo("0");
    }

    @Test
    void ac4_noAccountsIsPending() {
        assertThat(AccountSummary.of(List.of())).isEqualTo(new AccountSummary(Status.PENDING, null, null));
    }

    @Test
    void ac11_onlyCheckingIsPending() {
        assertThat(AccountSummary.of(List.of(account(AccountType.CHECKING, "5.00"))))
                .isEqualTo(new AccountSummary(Status.PENDING, null, null));
    }

    @Test
    void ac11_onlySavingsIsPending() {
        assertThat(AccountSummary.of(List.of(account(AccountType.SAVINGS, "5.00"))))
                .isEqualTo(new AccountSummary(Status.PENDING, null, null));
    }
}
