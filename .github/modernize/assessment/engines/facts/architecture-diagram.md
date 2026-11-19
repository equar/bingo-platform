# Architecture Diagram

This deployment architecture shows how infrastructure is provisioned separately
from application releases, and how the test and production environments differ.
Mobile store uploads are separate manual workflows with production provenance
checks before promotion or App Store submission.

## Application Architecture

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
```mermaid
flowchart TD
    subgraph aInputs["Change and operator inputs"]
        aPR["Reviewed pull request"]
        aChecks["PR validation"]
        aDevelop["develop branch"]
        aRelease["release branch"]
        aOperator["Terraform operator"]
        aPat["Local GoDaddy token"]
        aGitHubConfig["GitHub environment settings"]
        aProdApproval["Protected production environment"]
    end
    subgraph aProvision["Infrastructure provisioning"]
        aTestApply["Terraform test plan and apply"]
        aProdApply["Terraform production plan and apply"]
    end
    subgraph aTest["Test environment"]
        aTestHost["EC2 host with Elastic IP"]
        aTestDb[("Local MySQL bingotest")]
        aTestDns["GoDaddy or Route 53 A records"]
        aTestUrls["HTTP site admin and API"]
    end
    subgraph aProd["Production environment"]
        aProdHost["Lightsail host and static IP"]
        aProdDb[("Private Lightsail MySQL bingo")]
        aProdDns["Production DNS records"]
        aProdTls["Nginx and TLS host setup"]
        aProdUrls["HTTPS site admin and API"]
    end
    subgraph aWebRelease["Backend and web application release"]
        aDeployWorkflow["Build and Deploy Bingo"]
        aTestDeploy["Test deployment"]
        aProdDeploy["Production deployment"]
    end
    subgraph aMobile["Mobile store release"]
        aTestMobile["Develop upload using test API"]
        aProdMobile["Release upload using production API"]
        aTestTracks["Play internal track or TestFlight"]
        aProvenance["Successful release provenance"]
        aStoreApproval["Production store approval environment"]
        aStorePromotion["Production promotion or submission"]
        aProductionStores["Google Play production or App Store review"]
    end

    aPR -->|"runs checks before merge"| aChecks
    aChecks -->|"permits merge"| aDevelop
    aChecks -->|"permits merge"| aRelease
    aDevelop -->|"merge selects test"| aDeployWorkflow
    aRelease -->|"merge selects production"| aDeployWorkflow
    aGitHubConfig --> aDeployWorkflow
    aDeployWorkflow -->|"develop branch"| aTestDeploy
    aDeployWorkflow -->|"release branch"| aProdDeploy
    aProdApproval --> aProdDeploy

    aOperator -->|"plans and applies"| aTestApply
    aOperator -->|"plans and applies"| aProdApply
    aPat -->|"used only when GoDaddy is selected"| aTestApply
    aTestApply -->|"creates host and associates EIP"| aTestHost
    aTestApply -->|"syncs A records after EIP association"| aTestDns
    aTestHost --> aTestDb
    aTestHost --> aTestUrls
    aTestDns --> aTestUrls
    aTestDeploy -->|"SSH deploy"| aTestHost

    aProdApply -->|"creates host and static IP"| aProdHost
    aProdApply --> aProdDb
    aProdApply -->|"outputs production static IP"| aProdDns
    aProdDns --> aProdTls
    aProdHost --> aProdTls
    aProdTls --> aProdUrls
    aProdHost --> aProdUrls
    aProdDeploy -->|"SSH deploy"| aProdHost

    aDevelop -.->|"manual dispatch"| aTestMobile
    aRelease -.->|"manual dispatch with HTTPS API"| aProdMobile
    aTestMobile --> aTestTracks
    aProdMobile --> aTestTracks
    aProdMobile --> aProvenance
    aProvenance --> aStorePromotion
    aStoreApproval --> aStorePromotion
    aStorePromotion --> aProductionStores
```

### Deployment sequence

| Lane | Flow |
|---|---|
| Test infrastructure | Create `env/test.tfvars`, run `./provision.sh test plan`, review, then apply. Terraform creates EC2, local MySQL, Nginx, and an Elastic IP. It syncs the three test A records through GoDaddy when `route53_zone_id` is empty, or creates Route 53 records when it is set. DNS records are not deleted on destroy. |
| Test application | Create and configure the GitHub `test` Environment. After PR validation, merge to `develop`; `deploy.yml` validates settings, builds changed backend/Admin/site components, and deploys them over SSH. Test uses schema `bingotest`, API port `8091`, and HTTP origins. |
| Production infrastructure | Create `env/prod.tfvars`, provide `TF_VAR_database_admin_password` securely, run `./provision.sh prod plan`, review, then apply. Terraform creates the Lightsail host, static IP, private MySQL database, and a Secrets Manager placeholder. Production DNS and HTTPS host setup are separate steps. |
| Production application | Create and protect the GitHub `production` Environment. After PR validation, merge to `release`; the deployment job waits for required environment approval before deploying changed components over SSH. Production uses schema `bingo`, API port `8081`, HTTPS, and verified database TLS. |
| Mobile testing | Manually dispatch Android internal testing or iOS TestFlight from `develop`; the default backend is `http://api.test.bakaroo.com`. |
| Mobile production | Dispatch the upload workflow from `release` with `https://api.bingo.bakaroo.com`. The workflow records provenance. Android promotion and iOS App Store submission verify that provenance and require their production environment approvals. |

**Production database gate:** the current repository-level `MYSQL_HOST` is an
RDS endpoint, but the Terraform production stack creates a Lightsail MySQL
database. The application workflow does not migrate data or create a
production application account. Select and verify the production database
target, app user, grants, credentials, migration, and rollback plan before
pointing the production deployment at the Terraform output.

### Technology Stack Summary

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| CI and deployment | GitHub Actions | Workflow managed | PR checks, builds, SSH deployments, and manual store workflows |
| Infrastructure | Terraform and AWS provider | Terraform `>= 1.1.0`, AWS provider `~> 5.0` | Provisions test EC2 and production Lightsail resources |
| Test host | Ubuntu, EC2, Nginx, MySQL | Ubuntu 24.04, `t3.micro`, MySQL 8 | HTTP test site and backend with local database |
| Production host | Ubuntu, Lightsail, Nginx, MySQL | Ubuntu 24.04, MySQL 8 | HTTPS production services and private managed database |
| Backend | Java, Spring Boot, Maven | Java 25, Spring Boot 4.1.1 | REST and WebSocket API |
| Admin | React, TypeScript, Vite | React 18, TypeScript 5, Node 22.22.0 | Admin single-page application |
| Public website | Astro, TypeScript | Astro 5, Node 22.22.0 | Public website |
| Mobile | Flutter and Fastlane | Flutter 3.47.7, Fastlane 2.241.1 | Android and iOS store artifacts and promotion |
| DNS | GoDaddy Domains API or AWS Route 53 | GoDaddy API v3 | Test A records; production DNS is separately configured |

### Data Storage & External Services

Test stores data in the `bingotest` MySQL schema on the EC2 host, bound to
loopback. Production uses the `bingo` schema in a private Lightsail MySQL
database with TLS identity verification. GitHub Actions supplies application
secrets at deployment time. Terraform state can contain sensitive production
resource attributes, so it must be protected and encrypted. GoDaddy DNS sync
uses `GODADDY_PAT` from the local Terraform process; the token is not a Terraform
variable or state value. No message broker or shared cache is part of this
deployment path.

### Key Architectural Decisions

- Infrastructure provisioning and application deployment are separate
  operations; application workflows never apply Terraform.
- Test is HTTP-only and isolated from production hostnames and data. Production
  is HTTPS-only and uses database TLS identity verification.
- Mobile store publication is manual and separate from web/backend deployment.
  Production promotion or submission requires a matching successful
  release-branch upload provenance record.

## Component Relationships

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
```mermaid
flowchart LR
    subgraph cValidation["Change validation"]
        cMerged["Merged pull request"]
        cPRChecks["PR validation pipeline"]
        cDetector["Changed component detector"]
        cConfig["Deployment config validator"]
    end
    subgraph cBuild["Application build"]
        cMaven["Maven backend build"]
        cAdminBuild["Node Admin build"]
        cSiteBuild["Node website build"]
    end
    subgraph cDelivery["Secure delivery"]
        cWorkflow["GitHub deploy workflow"]
        cBackendDeploy["Backend deploy script"]
        cFrontendDeploy["Frontend deploy script"]
        cSSH["SSH transport"]
        cEnvironment["GitHub environment gate"]
    end
    subgraph cRuntime["Host runtime"]
        cHost["Ubuntu environment host"]
        cNginx["Nginx routing"]
        cApi["Spring Boot API"]
        cStatic["Admin and site roots"]
        cDatabase[("Environment MySQL schema")]
    end
    subgraph cInfra["Infrastructure and DNS"]
        cTerraform["Terraform provision wrapper"]
        cAws["EC2 test or Lightsail production"]
        cDns["GoDaddy or Route 53"]
        cToken["Local GoDaddy PAT"]
    end
    subgraph cMobile["Mobile store controls"]
        cFlutter["Flutter build and signing"]
        cUpload["Fastlane upload"]
        cManifest["Release provenance artifact"]
        cVerify["Production provenance check"]
        cStore["Play or App Store"]
    end

    cPRChecks -->|"required before merge"| cMerged
    cMerged --> cWorkflow
    cWorkflow --> cEnvironment
    cEnvironment --> cDetector
    cDetector --> cConfig
    cConfig --> cMaven
    cConfig --> cAdminBuild
    cConfig --> cSiteBuild
    cMaven --> cBackendDeploy
    cAdminBuild --> cFrontendDeploy
    cSiteBuild --> cFrontendDeploy
    cBackendDeploy --> cSSH
    cFrontendDeploy --> cSSH
    cSSH --> cHost
    cHost --> cNginx
    cNginx --> cApi
    cNginx --> cStatic
    cApi --> cDatabase

    cTerraform --> cAws
    cAws --> cHost
    cToken --> cTerraform
    cTerraform -->|"test DNS sync when selected"| cDns

    cFlutter --> cUpload
    cUpload --> cManifest
    cManifest --> cVerify
    cVerify --> cStore
```

### Component Inventory

| Component | Layer | Type | Responsibility |
|---|---|---|---|
| PR validation pipeline | Validation | GitHub Actions workflow | Tests the proposed change before merge |
| Build and Deploy Bingo | Delivery | GitHub Actions workflow | Selects test or production from the branch and deploys selected applications |
| Changed component detector | Validation | Python script | Limits builds and deployments to changed components |
| Deployment config validator | Validation | Python script | Checks environment-specific origins, bootstrap, and email settings |
| Maven backend build | Build | Maven | Produces the Spring Boot JAR |
| Admin and website builds | Build | Node toolchains | Produce static deployment artifacts |
| Backend deploy script | Delivery | Bash and SSH | Validates runtime configuration, preflights migrations, deploys and health-checks the JAR |
| Frontend deploy script | Delivery | Bash and SSH | Preflights public URLs and replaces selected static roots with backups |
| Terraform provision wrapper | Infrastructure | Bash and Terraform | Selects the environment workspace and applies reviewed infrastructure plans |
| GoDaddy DNS updater | Infrastructure | Python and HTTPS API | Creates or updates test A records without deleting them on destroy |
| Nginx | Runtime | Reverse proxy and static server | Serves the public origins and proxies API traffic to loopback |
| Spring Boot API | Runtime | Java service | Serves REST and WebSocket requests |
| MySQL schema | Data | MySQL database | Stores test `bingotest` or production `bingo` data |
| Mobile upload workflows | Release | Flutter and Fastlane | Builds and uploads internal testing or TestFlight artifacts |
| Release provenance check | Release gate | Python verification | Rejects test-backend builds from production promotion or submission |
