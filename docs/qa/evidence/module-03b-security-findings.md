# Module 03B — Authentication Findings

## F01 — Non-player roles could authenticate through the player login route

**Reproduction:** The existing admin authorization integration path authenticated an ADMIN with `POST /api/v1/auth/login`, then used the returned ADMIN token on `/api/v1/admin/users`. Source review found that `AuthService.login` singled out AGENT but otherwise delegated any active account to Spring AuthenticationManager.

**Root cause:** The player endpoint rejected one non-player role rather than requiring `PLAYER`.

**Fix and regression:** The service now returns the same generic credentials failure for any account without `PLAYER`. The dedicated MySQL regression verifies ADMIN and AGENT both receive `AUTH_INVALID_CREDENTIALS` / 401. The admin endpoint authorization fixture now obtains a role token directly so it tests authorization without relying on the player login route.

## F02 — Successful login did not reset failed PIN attempts

**Reproduction:** Before the change, the integration regression explicitly verified that a seeded account failure count survived a successful login.

**Root cause:** Failed authentication recorded account attempts, but the success path did not clear them.

**Fix and regression:** Successful player login clears the hash-keyed account bucket after credential and status checks. The MySQL test confirms this account bucket is removed and the independent network failure bucket remains.

## F03 — Login 401 was presented as an expired session

**Reproduction:** A PENDING player login returned 401 while no bearer token existed, and the app showed a session-expired banner above the ordinary invalid-credentials message. See [captured Android screen](module-03b-invalid-credentials-session-banner-defect.png).

**Root cause:** The Dio error interceptor cleared the local session on every 401, including unauthenticated login calls.

**Fix and regression:** The interceptor clears session state only when the failed request carried a bearer token. Android integration now verifies invalid PIN and restricted-account errors do not display the expired-session banner.

## Residual logout behavior

There is no server logout endpoint, refresh-token store, or JWT revocation list. The verified logout behavior is local token/profile removal. An already-issued token copied before logout remains valid until expiry while the account remains active; no server-side revocation claim is made.
