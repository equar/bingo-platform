# Module 01 — Agent Login Lifecycle Defect

Date: 2026-10-23

## Reproduction

The Android agent login flow was submitted with an invalid credential. The controller marked the entire auth gate as loading while awaiting the request, so `AuthGate` replaced and disposed the login form. When the request failed, `AuthScreen._submit` attempted `setState` on the disposed state. The device run reported:

```text
setState() called after dispose(): _AuthScreenState
AuthScreen._submit (lib/src/features/auth/presentation/auth_screen.dart)
```

## Fix

Login progress remains local to the sign-in form while the request is pending. The controller changes authenticated state only after a successful response, and the error handler checks `mounted` before updating the form.

## Regression verification

The final [agent authentication run](module-01-agent-run.log) passed on `emulator-5554`. It exercised offline failure, invalid phone, repeated failures, invalid PIN, approved login, pending-agent rejection, expired token cleanup, and session persistence. The [agent invalid-login screenshot](module-01-agent-network-error.png) and [pending-agent rejection screenshot](module-01-agent-pending-rejected.png) show the resulting UI states.
