package com.bankdash.profile.api;

import com.bankdash.profile.app.ProfileService;
import com.bankdash.profile.domain.Preferences;
import com.bankdash.profile.domain.Preferences.Currency;
import com.bankdash.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/preferences")
class PreferencesController {

    private final ProfileService profiles;

    PreferencesController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    PreferencesResponse get(@CurrentUser UUID userId) {
        return PreferencesResponse.of(profiles.preferences(userId));
    }

    @PutMapping
    PreferencesResponse update(@CurrentUser UUID userId, @Valid @RequestBody PreferencesRequest request) {
        return PreferencesResponse.of(profiles.update(request.toPreferences(userId)));
    }

    /** All fields are required: the form always sends the whole tab. */
    record PreferencesRequest(
            // A string, not the enum, so that an unknown currency is a field error rather than unreadable JSON.
            @NotNull @Pattern(regexp = "USD|EUR|GBP", message = "Must be one of USD, EUR, GBP") String currency,
            @NotBlank @TimeZoneId String timeZone,
            @NotNull Boolean notifyOnTransfers,
            @NotNull Boolean notifyOnMerchantOrders,
            @NotNull Boolean notifyOnRecommendations) {

        Preferences toPreferences(UUID userId) {
            return new Preferences(userId, Currency.valueOf(currency), timeZone, notifyOnTransfers,
                    notifyOnMerchantOrders, notifyOnRecommendations);
        }
    }

    record PreferencesResponse(
            Currency currency,
            String timeZone,
            boolean notifyOnTransfers,
            boolean notifyOnMerchantOrders,
            boolean notifyOnRecommendations) {

        static PreferencesResponse of(Preferences p) {
            return new PreferencesResponse(p.currency(), p.timeZone(), p.notifyOnTransfers(), p.notifyOnMerchantOrders(),
                    p.notifyOnRecommendations());
        }
    }
}
