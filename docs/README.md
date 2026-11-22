# Documentation

Account preferences include a persisted `preferredCalendar` value (`AUTO`, `ETHIOPIAN`, or `GREGORIAN`) returned by authenticated profile APIs. `AUTO` resolves to Ethiopian for Amharic or Tigrigna profile languages and Gregorian otherwise. Ethiopian display dates retain the platform's Ethiopia wall-time convention; API and export date representations remain canonical Gregorian/ISO. Tigrigna resources are incomplete and are not offered as a selectable UI language until localization is complete.

Start with the root [README](../README.md) for prerequisites and local quick start.

## Architecture and business rules

- [Business-domain inventory and historical coverage](qa/master-business-domain-inventory.md)
- [Community features and rules](community-features.md)
- [Backend API, OpenAPI, and live-draw contract](../bingo-backend/README.md)

## Development and client guides

- [Admin](../bingo-admin/README.md), [Player](../bingo-player/README.md),
  and [website](../bingo-website/README.md)

## Deployment and operations

- [Production deployment](../deployment/README.md)
- [One-time Admin bootstrap](../deployment/aws/ADMIN_BOOTSTRAP.md)
- [Mobile package and store release](../deployment/mobile/README.md)
- [Local QA V22→V39 schema upgrade and recovery runbook](qa/local-bingo-qa-schema-upgrade-runbook.md)

## Testing and release controls

- [QA index](qa/README.md)
- [Guarded real E2E runbook](qa/platform-e2e-runbook.md)
- [Current E2E scenario matrix](qa/platform-e2e-scenario-matrix.md)
- [Current blockers and approvals](qa/qa-blockers-and-approvals.md)
- [Financial controls and reconciliation](qa/financial-reconciliation.md)
