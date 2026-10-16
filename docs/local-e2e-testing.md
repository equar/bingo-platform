# Local E2E Testing

This workspace includes a local end-to-end harness that exercises the real backend, the admin console, and the player app.

## Prerequisites

- Backend running on `http://localhost:8080` with the `dev` profile enabled.
- Admin console running on `http://localhost:5173`.
- The player app available for Flutter widget tests against the local backend.

## Run

Use the orchestration script from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-local-e2e.ps1
```

The script creates a run directory under `test-results/local-e2e/<run-id>/` and coordinates these phases in order:

1. Admin session setup.
2. Player deposit and purchase setup.
3. Deposit review in the admin console.
4. Winning draw posting.
5. Winning claim submission from the player app.
6. Claim approval and prize settlement in the admin console.
7. Final player verification.

## Shared State

The harness stores cross-phase state in a manifest JSON file. The default location is generated per run, but you can override it with `BINGO_E2E_MANIFEST_PATH`.

## Notes

- The harness uses the real development seed player credentials from the backend dev profile.
- Generated artifacts are ignored by git.
- The harness preserves existing data and does not reset the database.
- On Windows, Flutter desktop integration tests require Developer Mode for plugin symlink support.
- If that setting is unavailable, the admin browser phases can still be run independently, but the player integration phases will not launch on this machine.
