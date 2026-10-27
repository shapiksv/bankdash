package com.bankdash.account.api;

import com.bankdash.account.app.AccountService;
import com.bankdash.account.domain.Account;
import com.bankdash.account.domain.AccountSummary;
import com.bankdash.web.CurrentUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    /** Always 200: a customer whose accounts are not opened yet is PENDING, not a 404. */
    @GetMapping("/summary")
    AccountSummaryResponse summary(@CurrentUser UUID userId) {
        return AccountSummaryResponse.of(accounts.summary(userId));
    }

    /** Amounts are JSON strings with 2 decimals in plain notation, so no client ever rounds them as floats. */
    record AccountSummaryResponse(
            String status, String currency, @Nullable String myBalance, @Nullable String totalSaving) {

        static AccountSummaryResponse of(AccountSummary summary) {
            return new AccountSummaryResponse(summary.status().name(), Account.CURRENCY, text(summary.myBalance()),
                    text(summary.totalSaving()));
        }

        /** UNNECESSARY fails loudly if a balance ever had more than 2 decimals, instead of rounding money. */
        private static @Nullable String text(@Nullable BigDecimal amount) {
            return amount == null ? null : amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        }
    }
}
