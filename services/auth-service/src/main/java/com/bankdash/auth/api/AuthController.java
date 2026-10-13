package com.bankdash.auth.api;

import com.bankdash.auth.app.AuthService;
import com.bankdash.auth.app.InvalidRefreshTokenException;
import com.bankdash.auth.app.Session;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final AuthService auth;
    private final RefreshCookies cookies;

    AuthController(AuthService auth, RefreshCookies cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/signup")
    ResponseEntity<TokenResponse> signup(@Valid @RequestBody SignupRequest request) {
        return respond(HttpStatus.CREATED, auth.signup(request.fullName(), request.email(), request.password()));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(HttpStatus.OK, auth.login(request.email(), request.password()));
    }

    /** Authenticated by the refresh cookie only. */
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        return respond(HttpStatus.OK, auth.refresh(refreshCookie(request)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookies.name());
        if (cookie != null && !cookie.getValue().isEmpty()) {
            auth.logout(cookie.getValue());
        }
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.findUser(UUID.fromString(jwt.getSubject()))
                .map(user -> new MeResponse(user.id(), user.email()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User no longer exists"));
    }

    private String refreshCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, cookies.name());
        if (cookie == null || cookie.getValue().isEmpty()) {
            throw new InvalidRefreshTokenException();
        }
        return cookie.getValue();
    }

    private ResponseEntity<TokenResponse> respond(HttpStatus status, Session session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken(), session.refreshTokenExpiresAt()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(TokenResponse.of(session));
    }

    record SignupRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            // Length rules live in PasswordPolicy (they count bytes, not characters).
            @NotNull String password) {}

    record LoginRequest(@NotBlank String email, @NotNull String password) {}

    record MeResponse(UUID id, String email) {}

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {

        static TokenResponse of(Session session) {
            return new TokenResponse(
                    session.accessToken().value(), "Bearer", session.accessToken().expiresIn().toSeconds());
        }
    }
}
