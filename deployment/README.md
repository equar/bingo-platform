# Bingo deployment

This directory contains the production deployment scripts and templates for the Bingo backend, admin app, and public website.

The layout follows the same general pattern as the BillPay deployment folder:

- separate backend and frontend deploy scripts
- versioned release directories
- `systemd` for the backend process
- `nginx` for the frontend host and reverse proxy
- env-file driven secrets handling

## What is deployed

- `bingo-backend` Spring Boot 3.3 / Java 17 / Maven / MySQL / Flyway
- `bingo-admin` React / TypeScript / Vite SPA
- `bingo-website` Astro static site deployed to AWS S3, with optional CloudFront invalidation

The Flutter player app and the agent app are intentionally not part of this deployment set.

## Files

- [backend/build-backend.sh](backend/build-backend.sh)
- [backend/deploy-backend.sh](backend/deploy-backend.sh)
- [backend/rollback-backend.sh](backend/rollback-backend.sh)
- [backend/health-check-backend.sh](backend/health-check-backend.sh)
- [backend/backend.env.example](backend/backend.env.example)
- [backend/bingo-backend.service](backend/bingo-backend.service)
- [admin/build-admin.sh](admin/build-admin.sh)
- [admin/deploy-admin.sh](admin/deploy-admin.sh)
- [admin/rollback-admin.sh](admin/rollback-admin.sh)
- [admin/health-check-admin.sh](admin/health-check-admin.sh)
- [admin/admin.env.example](admin/admin.env.example)
- [admin/nginx-bingo.conf](admin/nginx-bingo.conf)
- [deploy-all.sh](deploy-all.sh)
- [.github/workflows/deploy-website.yml](../.github/workflows/deploy-website.yml)
- [.github/workflows/deploy-aws.yml](../.github/workflows/deploy-aws.yml)

## Prerequisites

Local machine:

- Bash-compatible shell
- `ssh` and `scp`
- Maven 3.9+ and Java 17 for the backend build
- Node.js 18+ and npm for the admin build
- Access to the production SSH key

Server:

- Ubuntu or similar Linux host
- Java 17 runtime
- nginx
- MySQL reachable from the backend host
- DNS for the public hostname pointing at nginx
- HTTPS certificate, typically from Let's Encrypt

## Values you must supply

The scripts intentionally use placeholders. Replace them before deploying:

- `SERVER` or `--server` with your SSH target, for example `ubuntu@bingo.example.com`
- `SSH_KEY` or `--ssh-key` with the private key path
- `ADMIN_PUBLIC_URL` or `--public-url` with the public origin that serves the SPA and proxies `/api` and `/ws`
- `BACKEND_ENV_FILE` or `--backend-env-file` with a local env file that holds backend secrets
- MySQL host, database, username, and password
- A strong `JWT_SECRET`

If you want automatic deployment on merge to `main`, also configure these GitHub Actions settings:

- Repository or environment variable `BINGO_SERVER`
- Repository or environment variable `BINGO_ADMIN_PUBLIC_URL`
- Optional variables `BINGO_REMOTE_BACKEND_ROOT`, `BINGO_REMOTE_ADMIN_ROOT`, `BINGO_BACKEND_SERVICE_NAME`, `BINGO_SERVICE_USER`
- Secret `BINGO_SSH_KEY_B64` containing the base64-encoded SSH private key
- Secret `BINGO_BACKEND_ENV_FILE_B64` containing the base64-encoded backend env file

The workflow in [.github/workflows/deploy-aws.yml](../.github/workflows/deploy-aws.yml) runs automatically on push to `main` and can also be launched manually with a dry-run flag.

Recommended production values:

- backend port: `8081`
- backend runtime root: `/opt/bingo/backend`
- admin runtime root: `/var/www/bingo-admin`
- database name: `bingo`
- backend service name: `bingo-backend`

## Same-origin hosting model

This deployment assumes a single public origin such as `https://bingo.example.com`:

- `/` serves the admin SPA
- `/api/` proxies to the backend
- `/ws` proxies the authenticated STOMP WebSocket endpoint

That keeps `VITE_API_BASE_URL` simple and prevents the frontend from embedding any backend secrets.

## Backend configuration template

Start from [backend/backend.env.example](backend/backend.env.example) and save the real file outside source control.

Required settings:

- `SPRING_PROFILES_ACTIVE=prod`
- `SERVER_PORT=8081`
- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`
- `MYSQL_HOST`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `WEBSOCKET_ALLOWED_ORIGINS`

Notes:

- `JWT_SECRET` must be strong and unique.
- Do not use any fallback JWT secret in production.
- `APP_DEV_SEED_ENABLED=false` is included to keep development seed users off in production.
- Flyway remains enabled and should run on startup.
- Hibernate schema validation is required; do not use automatic schema modification in production.

## Admin configuration template

Start from [admin/admin.env.example](admin/admin.env.example).

Set `VITE_API_BASE_URL` to the public origin, not to a secret backend host.

Examples:

- `https://bingo.example.com`
- `https://admin.bingo.example.com` if the reverse proxy serves the SPA on a dedicated host

The Vite bundle must not include backend secrets.

## First-time server setup

1. Create the service user.

```bash
sudo useradd --system --create-home --shell /usr/sbin/nologin bingo
```

2. Create the runtime directories.

```bash
sudo install -d -o bingo -g bingo -m 755 /opt/bingo/backend /opt/bingo/backend/releases /opt/bingo/backend/logs
sudo install -d -o bingo -g bingo -m 755 /var/www/bingo-admin /var/www/bingo-admin/releases
```

3. Install the backend service.

```bash
sudo cp deployment/backend/bingo-backend.service /etc/systemd/system/bingo-backend.service
sudo systemctl daemon-reload
sudo systemctl enable bingo-backend
```

4. Install the nginx template.

```bash
sudo cp deployment/admin/nginx-bingo.conf /etc/nginx/sites-available/bingo
sudo ln -sf /etc/nginx/sites-available/bingo /etc/nginx/sites-enabled/bingo
sudo nginx -t
sudo systemctl reload nginx
```

5. Configure DNS and TLS.

- Point the public hostname at the nginx server.
- Use Let's Encrypt or your certificate provider for the HTTPS certificate paths referenced in the nginx template.

## Build and deploy

### Backend only

```bash
deployment/backend/deploy-backend.sh --backend-env-file /path/to/bingo-backend.prod.env
```

### Admin only

```bash
deployment/admin/deploy-admin.sh --public-url https://bingo.example.com
```

### Combined deployment

```bash
deployment/deploy-all.sh \
  --backend-env-file /path/to/bingo-backend.prod.env \
  --public-url https://bingo.example.com
```

## Dry run

Use `--dry-run` to validate the scripts and print the actions without changing the server.

```bash
deployment/deploy-all.sh --dry-run --backend-env-file /path/to/bingo-backend.prod.env --public-url https://bingo.example.com
```

Dry run behavior:

- builds still run locally
- no SSH or SCP transfer occurs
- no remote files or services are changed

## Health checks

Backend:

```bash
deployment/backend/health-check-backend.sh http://127.0.0.1:8081/actuator/health
```

Admin:

```bash
deployment/admin/health-check-admin.sh https://bingo.example.com/
```

Recommended server-side checks:

- `journalctl -u bingo-backend -n 200 --no-pager`
- `curl -fsS http://127.0.0.1:8081/actuator/health`
- `curl -fsS https://bingo.example.com/`
- `curl -fsS https://bingo.example.com/api/actuator/health`

## Logs

Backend:

```bash
sudo journalctl -u bingo-backend -f
```

nginx:

```bash
sudo tail -f /var/log/nginx/bingo_access.log /var/log/nginx/bingo_error.log
```

## Rollback

Rollback scripts switch the symlink back to the previous versioned release and restart or re-point the service.

Backend rollback:

```bash
deployment/backend/rollback-backend.sh
```

Admin rollback:

```bash
deployment/admin/rollback-admin.sh
```

Important:

- Rollback restores the application release only.
- Rollback does not automatically reverse Flyway migrations.
- Do not run destructive database rollback steps automatically.
- Preserve the database contents unless you explicitly plan a migration rollback.

## Differences from BillPay

The BillPay reference uses separate frontend and backend deployment scripts with nginx and `systemd`. Bingo follows the same pattern, but with these changes:

- Bingo uses a single public origin for the admin SPA, `/api`, and `/ws`.
- The backend runs on port `8081` instead of BillPay's port.
- The frontend build uses `VITE_API_BASE_URL` at build time, but only to point at the public origin.
- The backend scripts require explicit CORS and WebSocket origin lists and do not permit wildcard browser origins in production.
- Production deployment uses Hibernate validation, not schema updates.
