package com.bankdash.account.domain;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The Accounts page's My Balance (CHECKING) and Total Saving (SAVINGS). READY only when both accounts exist; a
 * customer whose accounts are not opened yet (or, which should not happen, who has only one) is PENDING.
 */
public record AccountSummary(Status status, @Nullable BigDecimal myBalance, @Nullable BigDecimal totalSaving) {

    public enum Status {
        READY,
        PENDING
    }

    public static AccountSummary of(List<Account> accounts) {
        BigDecimal checking = balanceOf(accounts, AccountType.CHECKING);
        BigDecimal savings = balanceOf(accounts, AccountType.SAVINGS);
        if (checking == null || savings == null) {
            return new AccountSummary(Status.PENDING, null, null);
        }
        return new AccountSummary(Status.READY, checking, savings);
    }

    private static @Nullable BigDecimal balanceOf(List<Account> accounts, AccountType type) {
        return accounts.stream().filter(a -> a.type() == type).map(Account::balance).findFirst().orElse(null);
    }
}
