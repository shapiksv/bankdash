package com.bankdash.profile.api;

import com.bankdash.profile.app.ProfileService;
import com.bankdash.profile.domain.ProfileChanges;
import com.bankdash.profile.domain.UserProfile;
import com.bankdash.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/profile/me")
class ProfileController {

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    ProfileResponse me(@CurrentUser UUID userId) {
        return ProfileResponse.of(profiles.profile(userId));
    }

    @PutMapping
    ProfileResponse update(@CurrentUser UUID userId, @Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.of(profiles.update(userId, request.toChanges()));
    }

    record ProfileRequest(
            @NotBlank @Size(max = 100) String fullName,
            // Case is ignored: usernames are stored lowercased.
            @NotBlank
            @Pattern(regexp = "\\s*[A-Za-z0-9._-]{3,30}\\s*", message = "Use 3 to 30 letters, digits, dots, dashes or underscores")
            String username,
            @Past @Nullable LocalDate dateOfBirth,
            @Size(max = 200) @Nullable String presentAddress,
            @Size(max = 200) @Nullable String permanentAddress,
            @Size(max = 100) @Nullable String city,
            @Size(max = 20) @Nullable String postalCode,
            @Size(max = 100) @Nullable String country) {

        ProfileChanges toChanges() {
            return new ProfileChanges(fullName, username, dateOfBirth, presentAddress, permanentAddress, city, postalCode, country);
        }
    }

    record ProfileResponse(
            UUID userId,
            String fullName,
            String username,
            @Nullable LocalDate dateOfBirth,
            @Nullable String presentAddress,
            @Nullable String permanentAddress,
            @Nullable String city,
            @Nullable String postalCode,
            @Nullable String country) {

        static ProfileResponse of(UserProfile p) {
            return new ProfileResponse(p.userId(), p.fullName(), p.username(), p.dateOfBirth(), p.presentAddress(),
                    p.permanentAddress(), p.city(), p.postalCode(), p.country());
        }
    }
}
