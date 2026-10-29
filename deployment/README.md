# Bingo deployment

Production follows the BillPay GitHub Actions/SSH pattern. A merged
feature-branch PR into `main` runs `.github/workflows/deploy.yml`: Java 25/Maven
packages `bingo-backend`, Node 22 builds `bingo-admin` and `bingo-website`,
and SSH uploads them to the Bingo server. MySQL database name is **bingo**.

- [Architecture and deployment behavior](aws/README.md)
- [Server, database, DNS, secrets, and release setup](aws/SETUP_CHECKLIST.md)
- [GitHub Actions](github-actions.md)

Use `deployment/aws/deploy-backend.sh` and `deploy-frontend.sh` for this path.
The older `deployment/backend`, `deployment/admin`, `deployment/deploy-all.sh`,
`deployment/lightsail`, and CloudFormation OIDC setup are legacy/reference
paths with different filesystem layouts. Do not mix them with production.
Flutter builds and store-publishing workflows remain separate.

For directly shared Android APKs and iOS Ad Hoc signing, see
[mobile packages](mobile/README.md).
