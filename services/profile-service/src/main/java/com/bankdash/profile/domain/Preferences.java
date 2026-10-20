package com.bankdash.profile.domain;

import java.util.UUID;

/** Settings → Preferences. Notification flags are read by notification-service (step 8). */
public record Preferences(
        UUID userId,
        Currency currency,
        String timeZone,
        boolean notifyOnTransfers,
        boolean notifyOnMerchantOrders,
        boolean notifyOnRecommendations) {

    public static Preferences defaults(UUID userId) {
        return new Preferences(userId, Currency.USD, "UTC", true, false, true);
    }

    /** Display currencies; balances are always USD. */
    public enum Currency {
        USD,
        EUR,
        GBP
    }
}
