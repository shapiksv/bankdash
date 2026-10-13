package com.bankdash.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankdash.testsupport.TestJwtIssuer;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The real gateway on a random port, with {@link TestJwtIssuer} as the JWKS source and a stub standing in for
 * auth-service. No Docker needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIT {

    static final TestJwtIssuer issuer = TestJwtIssuer.start();
    static final StubService authService = StubService.start();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("bankdash.services.auth", authService::url);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", issuer::jwksUri);
    }

    @AfterAll
    static void stop() {
        issuer.close();
        authService.close();
    }

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();
    final UUID user = UUID.randomUUID();

    @BeforeEach
    void reset() {
        authService.lastRequest.set(null);
    }

    HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    HttpResponse<String> me(String token) throws Exception {
        return send(request("/api/auth/me").header("Authorization", "Bearer " + token));
    }

    void assertRejected(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(v -> assertThat(v).startsWith("Bearer"));
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(authService.lastRequest.get()).as("request must not reach the service").isNull();
    }

    @Test
    void missingTokenIs401() throws Exception {
        assertRejected(send(request("/api/auth/me")));
    }

    @Test
    void malformedTokenIs401() throws Exception {
        assertRejected(me("not-a-jwt"));
    }

    @Test
    void tokenSignedByAnotherKeyIs401() throws Exception {
        assertRejected(me(issuer.tokenSignedByUnknownKey(user)));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        assertRejected(me(issuer.expiredToken(user)));
    }

    @Test
    void wrongAudienceIs401() throws Exception {
        assertRejected(me(issuer.token(user, claims -> claims.audience("someone-else"))));
    }

    @Test
    void wrongIssuerIs401() throws Exception {
        assertRejected(me(issuer.token(user, claims -> claims.issuer("evil"))));
    }

    @Test
    void validTokenIsForwardedWithThePrefixStripped() throws Exception {
        String token = issuer.token(user);

        var response = me(token);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(authService.lastRequest.get().path()).isEqualTo("/auth/me");
        assertThat(authService.lastRequest.get().authorization()).isEqualTo("Bearer " + token);
    }

    @Test
    void publicAuthEndpointsNeedNoToken() throws Exception {
        var response = send(request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(authService.lastRequest.get().path()).isEqualTo("/auth/login");
        // The refresh cookie passes through untouched; its Path is already the browser-facing /api/auth.
        assertThat(response.headers().firstValue("Set-Cookie")).hasValue(StubService.COOKIE);
    }

    @Test
    void theRefreshCookieReachesAuthService() throws Exception {
        var response = send(request("/api/auth/refresh")
                .header("Cookie", "bd_refresh=abc")
                .POST(HttpRequest.BodyPublishers.noBody()));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(authService.lastRequest.get().cookie()).isEqualTo("bd_refresh=abc");
    }

    @Test
    void anOnlyPublicMethodIsPublic() throws Exception {
        // GET on a public POST endpoint still needs a token.
        assertRejected(send(request("/api/auth/login")));
    }

    @Test
    void corsPreflightFromTheWebAppIsAllowedWithCredentials() throws Exception {
        var response = send(request("/api/auth/me")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
    }

    @Test
    void corsPreflightFromAnotherOriginIsRejected() throws Exception {
        var response = send(request("/api/auth/refresh")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void aNewSigningKeyIsPickedUpWithoutRestart() throws Exception {
        assertThat(me(issuer.token(user)).statusCode()).isEqualTo(200);

        issuer.rotateKey();

        assertThat(me(issuer.token(user)).statusCode()).isEqualTo(200);
    }

    record SeenRequest(String path, String authorization, String cookie) {}

    /** Records the last request and answers 200, with a refresh cookie on /auth/login. */
    static final class StubService implements AutoCloseable {

        static final String COOKIE = "bd_refresh=xyz; Path=/api/auth; Max-Age=604800; HttpOnly; SameSite=Strict";

        final HttpServer server;
        final AtomicReference<SeenRequest> lastRequest = new AtomicReference<>();

        private StubService(HttpServer server) {
            this.server = server;
        }

        static StubService start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                StubService stub = new StubService(server);
                server.createContext("/", exchange -> {
                    var headers = exchange.getRequestHeaders();
                    stub.lastRequest.set(new SeenRequest(exchange.getRequestURI().getPath(),
                            headers.getFirst("Authorization"), headers.getFirst("Cookie")));
                    if (exchange.getRequestURI().getPath().equals("/auth/login")) {
                        exchange.getResponseHeaders().add("Set-Cookie", COOKIE);
                    }
                    byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
                server.start();
                return stub;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
