# Module 03C — Player Profile Management

## Product surface

The player app profile is a read-only view of the signed-in player's display name, phone number, roles, and account creation date (“Member since”). These fields are immutable through the player app because there is no profile edit screen or write endpoint. In particular, phone number changes are not supported; no phone recovery/verification design was introduced. The profile request uses `GET /api/v1/me`; the backend obtains identity from the authenticated principal and does not accept a target player ID. The response contains profile/account metadata and excludes credential and session secrets.

No profile editor is present, and the backend has no `PUT` or `PATCH` profile route. The original 03C matrix recorded profile-write scenarios P11–P18, P20, P24–P25, and P28 as NOT APPLICABLE; Module 03E reclassifies these as an **open product gap**, because those requested capabilities do not exist and have not passed. Protected-field rejection and no-partial-write behavior are still checked by attempting `PUT /api/v1/me`: it returns HTTP 405, and persisted name, phone, roles, and status remain unchanged. Product must make an explicit scope decision before editable profile behavior can be implemented and verified.

## App behavior

The profile first renders the locally retained signed-in identity while requesting the server profile. A successful response refreshes the displayed fields. The refresh button and pull-to-refresh repeat the request. On failure, the cached identity stays visible with a localized error and retry control. Logout still clears the session. No profile write or wallet operation occurs.

## Defect fixed

An unsupported `PUT /api/v1/me` reached the generic exception handler and returned HTTP 500. The global handler now maps `HttpRequestMethodNotSupportedException` to HTTP 405 `METHOD_NOT_ALLOWED`. The regression test confirms the rejection and verifies the player's stored name, phone, role, and status remain unchanged.

## Persistence and audit

The read-only `bingo_qa` reconciliation found player #178 and #179 active, both with ETB 0.00 available/promotional balance, zero signup grants, zero wallet transactions, and no profile-update audit rows. Profile GET itself does not create a profile update audit event. Authentication login telemetry remains ordinary auth audit data. See the raw SELECT script and output in the evidence folder.

## Remaining product gap

Player profile editing is not implemented. If product requirements need editable fields, a scoped edit/API feature and its validation, phone verification/recovery policy, protected-field authorization, persistence, and audit behavior must be implemented before the update scenarios can be run. Module 03E records this as an open product gap; it is outside the approved view-only QA scope. Module 02 remains separate and incomplete.

## Verification

See the [P01–P28 matrix](module-03c-test-matrix.md), [Android results](../evidence/module-03c-android-results.txt), [backend results](../evidence/module-03c-backend-results.txt), and [read-only database reconciliation](../evidence/module-03c-database-readonly.txt). Screenshots are stored alongside those artifacts. Persistent QA records remain unchanged by this view-only module.
