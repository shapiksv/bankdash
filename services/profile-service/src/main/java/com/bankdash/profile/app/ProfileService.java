package com.bankdash.profile.app;

import com.bankdash.events.UserRegistered;
import com.bankdash.profile.domain.Preferences;
import com.bankdash.profile.domain.PreferencesRepository;
import com.bankdash.profile.domain.ProfileRepository;
import com.bankdash.profile.domain.UserProfile;
import com.bankdash.profile.domain.Usernames;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profiles;
    private final PreferencesRepository preferences;
    private final Clock clock;

    public ProfileService(ProfileRepository profiles, PreferencesRepository preferences, Clock clock) {
        this.profiles = profiles;
        this.preferences = preferences;
        this.clock = clock;
    }

    /**
     * Creates the profile and default preferences of a newly registered user. A profile that already exists is
     * left as it is, so a redelivered event cannot undo the user's edits.
     *
     * <p>Two registrations racing for the same default username make one insert fail on the unique index; the
     * listener's retry then sees the name as taken and picks the next candidate.
     */
    @Transactional
    public void createFromRegistration(UserRegistered event) {
        String username = Usernames.candidates(event.email(), event.userId())
                .filter(candidate -> !profiles.usernameTaken(candidate))
                .findFirst()
                .orElseThrow();
        UserProfile profile = UserProfile.registered(event.userId(), event.fullName(), username, clock.instant());
        if (profiles.insertIfAbsent(profile)) {
            preferences.insertIfAbsent(Preferences.defaults(event.userId()));
            log.info("Created profile {} for user {}", username, event.userId());
        }
    }
}
