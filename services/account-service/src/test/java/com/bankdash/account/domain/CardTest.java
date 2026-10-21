package com.bankdash.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardTest {

    final Instant now = Instant.parse("2026-10-21T10:00:00Z");
    final YearMonth october2026 = YearMonth.of(2026, 10);

    Card issue(String pinHash) {
        return Card.issue(UUID.randomUUID(), UUID.randomUUID(), "Jane Doe", "5678", october2026, pinHash, now);
    }

    @Test
    void ac1_expiresThreeYearsAfterIssue() {
        Card card = issue(null);

        assertThat(card.expiry()).isEqualTo(YearMonth.of(2029, 10));
        assertThat(card.expiryText()).isEqualTo("10/29");
        assertThat(card.type()).isEqualTo(CardType.DEBIT);
    }

    @Test
    void ac1_aCardWithoutPinIsInactive() {
        assertThat(issue(null).status()).isEqualTo(CardStatus.INACTIVE);
        assertThat(issue("$2a$hash").status()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    void ac4_reportsExpiredAfterTheExpiryMonth() {
        Card card = issue("$2a$hash");

        assertThat(card.displayStatus(YearMonth.of(2029, 10))).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.displayStatus(YearMonth.of(2029, 11))).isEqualTo(CardStatus.EXPIRED);
        assertThat(issue(null).displayStatus(YearMonth.of(2030, 1))).isEqualTo(CardStatus.EXPIRED);
    }

    @Test
    void ac4_blockedWinsOverExpired() {
        Card blocked = issue("$2a$hash").blocked(now);

        assertThat(blocked.displayStatus(october2026)).isEqualTo(CardStatus.BLOCKED);
        assertThat(blocked.displayStatus(YearMonth.of(2031, 1))).isEqualTo(CardStatus.BLOCKED);
    }

    @Test
    void ac12_isValidThroughTheLastDayOfTheExpiryMonth() {
        Card card = issue(null);

        assertThat(card.isExpired(YearMonth.of(2029, 10))).isFalse();
        assertThat(card.isExpired(YearMonth.of(2029, 11))).isTrue();
    }

    @Test
    void ac8_settingTheFirstPinActivates() {
        Instant later = now.plusSeconds(60);
        Card card = issue(null).withPin("$2a$hash", later);

        assertThat(card.status()).isEqualTo(CardStatus.ACTIVE);
        assertThat(card.pinHash()).isEqualTo("$2a$hash");
        assertThat(card.updatedAt()).isEqualTo(later);
    }
}
