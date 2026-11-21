# Email providers

All backend admin emails (bootstrap passwords, account activation links, and
password reset links) use `APP_EMAIL_PROVIDER`. Supported values are `smtp` and
`graph`; deployed environments must set the provider explicitly. The local
application default is `smtp`. Unsupported values fail validation and startup.
The provider is selected on deployment; changing GitHub settings requires
another deployment to update the running service. There is no automatic
provider fallback.

## Microsoft Graph

Set these variables in the selected GitHub Environment:

| Variable | Value |
|---|---|
| `APP_EMAIL_PROVIDER` | `graph` |
| `APP_EMAIL_FROM` | The authorized Microsoft 365 sender mailbox, e.g. `noreply@bakaroo.com` |

Set these environment secrets:

| Secret | Source |
|---|---|
| `MICROSOFT_GRAPH_TENANT_ID` | Microsoft Entra tenant ID |
| `MICROSOFT_GRAPH_CLIENT_ID` | Entra application/client ID |
| `MICROSOFT_GRAPH_CLIENT_SECRET` | Entra application client secret |

The backend uses the application client credentials flow with the fixed scope
`https://graph.microsoft.com/.default`, then posts to
`https://graph.microsoft.com/v1.0/users/{sender}/sendMail`. No SMTP password,
SMTP login, interactive sign-in, or delegated token is needed in Graph mode.
Only the `MICROSOFT_GRAPH_*` names are supported; set `APP_EMAIL_PROVIDER`
explicitly in each environment.

The Entra application needs the Microsoft Graph **application** permission
`Mail.Send` with tenant admin consent (or equivalent Exchange application RBAC),
and access to the selected sender mailbox. The mailbox must exist. An email
address in `ADMIN_BOOTSTRAP_EMAILS` is a recipient; it does not need to be the
sender mailbox. Scope app access to the required mailbox using Exchange controls.

Tokens are cached in memory until shortly before expiry. HTTP requests have
bounded timeouts; errors report status codes without response bodies, tokens,
client secrets, or message text. HTTP 202 means accepted for processing, not
confirmed inbox delivery. Bootstrap treats acceptance as success and skips that
account on later deployments; monitor bounces and use password recovery when needed.

## SMTP

Set `APP_EMAIL_PROVIDER=smtp`, and configure:

| Setting | GitHub type | Example |
|---|---|---|
| `SPRING_MAIL_HOST` | Variable | `smtp.office365.com` |
| `SPRING_MAIL_PORT` | Variable | `587` |
| `SPRING_MAIL_USERNAME` | Secret | SMTP login/mailbox |
| `SPRING_MAIL_PASSWORD` | Secret | Provider-supported password/app password |
| `APP_EMAIL_FROM` | Variable | Authorized sender mailbox; defaults to SMTP username |

The SMTP transport uses authentication and STARTTLS. Graph credentials are not
required in SMTP mode. Provider support and tenant policy determine which SMTP
credentials are accepted.

## References

- [Microsoft Graph sendMail](https://learn.microsoft.com/en-us/graph/api/user-sendmail?view=graph-rest-1.0)
- [Client credentials authentication](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-client-creds-grant-flow)
