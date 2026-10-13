package com.bankdash.auth.api;

import com.bankdash.auth.app.AuthService;
import com.bankdash.auth.app.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return respond(HttpStatus.CREATED, auth.signup(request.email(), request.password()));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return respond(HttpStatus.OK, auth.login(request.email(), request.password()));
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

    record TokenResponse(String accessToken, String tokenType, long expiresIn) {

        static TokenResponse of(Session session) {
            return new TokenResponse(
                    session.accessToken().value(), "Bearer", session.accessToken().expiresIn().toSeconds());
        }
    }
}
