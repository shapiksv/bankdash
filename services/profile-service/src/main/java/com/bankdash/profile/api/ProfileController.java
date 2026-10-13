package com.bankdash.profile.api;

import com.bankdash.profile.app.ProfileService;
import com.bankdash.profile.domain.Avatar;
import com.bankdash.profile.domain.AvatarImage;
import com.bankdash.profile.domain.ProfileChanges;
import com.bankdash.profile.domain.UserProfile;
import com.bankdash.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/profile/me")
class ProfileController {

    private final ProfileService profiles;

    ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    ProfileResponse me(@CurrentUser UUID userId) {
        return response(profiles.profile(userId));
    }

    @PutMapping
    ProfileResponse update(@CurrentUser UUID userId, @Valid @RequestBody ProfileRequest request) {
        return response(profiles.update(userId, request.toChanges()));
    }

    /**
     * The raw image as the request body, with its type as {@code Content-Type}. Reads at most one byte past the
     * limit, so an oversized upload is refused without buffering all of it.
     */
    @PutMapping(path = "/avatar", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.IMAGE_JPEG_VALUE, "image/webp"})
    ResponseEntity<Void> putAvatar(@CurrentUser UUID userId, HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > AvatarImage.MAX_BYTES) {
            throw new AvatarImage.AvatarTooLargeException();
        }
        byte[] data = request.getInputStream().readNBytes(AvatarImage.MAX_BYTES + 1);
        MediaType type = MediaType.parseMediaType(request.getContentType()); // without parameters such as charset
        profiles.setAvatar(userId, AvatarImage.of(type.getType() + "/" + type.getSubtype(), data));
        return ResponseEntity.noContent().build();
    }

    /** Fetched with the Bearer token and shown through an object URL (an img tag cannot send the token). */
    @GetMapping("/avatar")
    ResponseEntity<byte[]> avatar(@CurrentUser UUID userId) {
        Avatar avatar = profiles.avatar(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No avatar"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noCache().cachePrivate())
                .lastModified(avatar.updatedAt())
                .body(avatar.data());
    }

    private ProfileResponse response(UserProfile profile) {
        return ProfileResponse.of(profile, profiles.avatarUpdatedAt(profile.userId()).orElse(null));
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
            @Nullable String country,
            // Null without an avatar; changes with every upload, so the web app can refetch the image.
            @Nullable Instant avatarUpdatedAt) {

        static ProfileResponse of(UserProfile p, @Nullable Instant avatarUpdatedAt) {
            return new ProfileResponse(p.userId(), p.fullName(), p.username(), p.dateOfBirth(), p.presentAddress(),
                    p.permanentAddress(), p.city(), p.postalCode(), p.country(), avatarUpdatedAt);
        }
    }
}
