# 0001 — JWT access tokens and rotating refresh tokens

- **Status:** accepted (step 2)
- **Context:** auth-service issues its own tokens (no Keycloak). The browser talks to the API Gateway only, and
  every service validates tokens itself.

## Decision

### Lifetimes

| Token | Format | Lifetime | Where it lives |
|---|---|---|---|
| Access token | RS256 JWT: `iss=bankdash-auth`, `aud=bankdash-api`, `sub`=user id, `email`, `iat`, `exp`, `jti`; header `kid` | **15 minutes** | JS memory only |
| Refresh token | Opaque random 256-bit value (not a JWT) | **7 days sliding** from its last use, capped at **30 days** per login | httpOnly cookie; the DB stores only its SHA-256 |

- An access token cannot be revoked, so it is short-lived. 15 minutes keeps refresh traffic low.
- Only auth-service reads refresh tokens. Keeping them opaque and in the database makes them revocable.
- Validators allow Spring Security's default 60 s clock skew on `exp`/`nbf`.

### Rotation and reuse detection

- Every login (or signup) starts a **family**. `refresh_token` has `family_id`, `replaced_by`, `revoked`,
  `expires_at` and `family_expires_at`.
- `POST /auth/refresh` locks the presented row (`SELECT … FOR UPDATE`). If it is valid, it marks it as replaced
  and issues the next token in the same family, all in one transaction.
- Presenting a token that was **already replaced or revoked** means a copy is being replayed. auth-service
  revokes the **whole family** and answers 401, so both the attacker and the real user have to log in again
  (OAuth 2.0 Security BCP). That transaction commits even though the request fails.
- An expired token is just a 401. A token whose user is disabled revokes its family.
- `POST /auth/logout` revokes the family and clears the cookie.
- There is **no grace window**. The browser must never cause a false reuse itself, so the web client makes
  sure only one refresh runs at a time:
  - concurrent 401s in a tab share one in-flight refresh;
  - tabs take turns through a Web Lock (`navigator.locks`). The cookie is shared, so the next tab already sends
    the rotated token.

### RS256 keys in development

- **No key is committed.** If `bankdash.auth.private-key` (env `BANKDASH_AUTH_PRIVATEKEY`, e.g.
  `file:/run/secrets/jwt.pem`, an unencrypted PKCS#8 PEM) is set, auth-service loads the key from it.
- If it is not set, auth-service generates an **in-memory 2048-bit key** at startup and logs a WARN. Tests do the
  same.
- The `kid` is the key's JWK thumbprint. `GET /.well-known/jwks.json` publishes only the public part.
- **What a restart costs:** outstanding access tokens stop validating. The refresh tokens are opaque and live in
  Postgres, so the client's next silent refresh restores the session. The gateway refetches the JWKS when it
  sees an unknown `kid`, so it needs no restart.
- The JWKS is a list of keys, so key rotation can be added later without changing the contract.
- **Multiple auth-service instances need a shared, configured key.** A generated key exists only in the
  instance that created it, and each instance's JWKS publishes only its own key. With two instances, a token
  signed by one fails validation wherever the JWKS came from the other. Any deployment with more than one
  instance must set `bankdash.auth.private-key` to the same key on every instance. The ephemeral key is for
  a single local instance only.

### Trade-off: no grace window

A rotated token is dead the moment its successor exists. So if the client never receives the rotation
response, its next refresh presents the old token and counts as reuse.

**When it happens:**
- the response is lost on the network after auth-service committed (connection reset, mobile network
  switch, gateway timeout);
- the tab is closed or crashes between the request and storing the new cookie.

**Effect:** the whole family is revoked and the user must log in again on that device. Other devices are
unaffected. Nothing is exposed: it fails closed.

**Why we accept it:**
- It needs a failure in a narrow window and costs one login.
- A grace window would let a stolen token be replayed without detection for the length of that window.
- The client already prevents the common self-inflicted case (parallel refreshes) with single-flight and a
  Web Lock.

**If it shows up in practice:** add a short grace window, about 10–30 s. Re-presenting a just-rotated token
within the window would issue a new sibling in the same family instead of revoking it, and only reuse after
the window would revoke. This needs a `rotated_at` column (a new Flyway migration) and is a contained change
in `AuthService.refresh`.

### Refresh cookie through the gateway

- **Cookie:** `bd_refresh` with `HttpOnly`, `SameSite=Strict`, `Path=/api/auth` (sent only to refresh and
  logout) and `Max-Age` equal to the token's lifetime.
  - `Secure` is on by default and off in the `dev` profile (http://localhost). `bootRun` uses `dev`.
- **Who sets it:** auth-service. Because the gateway strips `/api`, auth-service takes the cookie path from
  config, which is the path the browser sees. The gateway passes `Set-Cookie` and `Cookie` through untouched.
- **Dev setup:** Vite proxies `/api` to the gateway. To the browser everything is one origin
  (`localhost:5173`), so the cookie is first-party and `SameSite=Strict` works.
- **CORS** is configured only at the gateway, for clients that call `:8080` directly. Origins are an explicit
  list (`bankdash.cors.allowed-origins`), never `*`, because credentials are allowed. Allowed methods are GET,
  POST, PUT and DELETE; allowed headers are `Authorization`, `Content-Type` and `Idempotency-Key`. Services never
  add CORS headers.
- **CSRF:** the only cookie-authenticated endpoints are refresh and logout.
  - `SameSite=Strict` stops cross-site requests from carrying the cookie.
  - The new access token comes back only in the response body, which another origin cannot read.
  - So the stateless API keeps server-side CSRF tokens disabled.

## Consequences

- Logging out on one device does not end other logins. Each login is its own family.
- **A lost refresh response logs the user out** (see *Trade-off: no grace window* below).
- Rate limiting on the auth endpoints is not done yet.
