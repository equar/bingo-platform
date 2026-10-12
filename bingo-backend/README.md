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

1. Create the `bingo` database in your local MySQL instance if it does not already exist.
2. Copy `.env.example` to `.env` and fill in the values if you need overrides.
3. Run the backend in the `dev` profile to enable development-only seed users:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Notes

- Public registration only creates player accounts.
- Admin and agent roles are not allowed through public registration.
- Password hashes, PIN hashes, and JWT secrets are never returned by the API.
- Database schema is managed with Flyway migrations.
- Local development uses localhost MySQL and does not require Docker.
