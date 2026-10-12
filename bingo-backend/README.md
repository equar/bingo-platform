# bingo-backend

Spring Boot 3 backend foundation for the Bingo platform.

## Stack

- Java 17
- Spring Boot 3
- Spring Security
- Spring Data JPA
- MySQL
- Flyway
- Bean Validation
- springdoc OpenAPI

## Local Development

1. Make sure MySQL is running locally and the configured user can create the `bingo` database if it does not already exist.
2. Copy `.env.example` to `.env` if you need to override the default local settings.
3. Run the backend normally, or with the `dev` profile to enable development-only seed users:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

By default, the backend points to `jdbc:mysql://localhost:3306/bingo`, uses the local `root` / `root` credentials, and enables `createDatabaseIfNotExist=true`, so the database is created on first connect when the MySQL account has permission.

## Notes

- Public registration only creates player accounts.
- Admin and agent roles are not allowed through public registration.
- Password hashes, PIN hashes, and JWT secrets are never returned by the API.
- Database schema is managed with Flyway migrations.
- Local development uses localhost MySQL and does not require Docker.
- The checked-in OpenAPI contract lives at [openapi.json](openapi.json); see [docs/openapi-integration.md](docs/openapi-integration.md) for how the three frontend projects should consume it.
