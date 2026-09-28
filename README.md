# Adventr
A project for me and my friends: a private, shared bucket list for friend groups.

Spring Boot 4 (Java 21) · Thymeleaf + htmx · PostgreSQL · Keycloak · Docker Compose + Caddy.
The spec lives in `openspec/changes/add-friend-adventures-mvp/`.

## Local development

Requirements: a JDK (21 or newer) and Docker. Maven comes with the wrapper (`./mvnw`).

1. Start Postgres, Keycloak, and Mailpit:

   ```sh
   docker compose -f docker-compose.dev.yml up -d
   ```

   Keycloak imports the `adventr` realm from `keycloak/realm-adventr.json` on its first start.
   Wait until `docker compose -f docker-compose.dev.yml ps` shows Keycloak as `healthy` (~30 s).

2. Run the app with the `dev` profile (in IntelliJ: set the active profile to `dev`):

   ```sh
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. Open http://localhost:8080, click **Log in or register**, then **Register** on the Keycloak page.

| What | URL | Credentials |
|---|---|---|
| App | http://localhost:8080 | your registered account |
| Keycloak admin console | http://localhost:8081/auth/admin | `admin` / `admin` |
| Mailpit (password-reset mails) | http://localhost:8025 | – |
| Postgres | `localhost:5432`, database `adventr` | `adventr` / `adventr-dev` |

All dev credentials are throwaway values from `docker-compose.dev.yml` and `application-dev.yml`.

The realm is imported only once. After editing `realm-adventr.json`, reset the dev stack with
`docker compose -f docker-compose.dev.yml down -v` (this deletes all dev data).

### Tests

```sh
./mvnw verify
```

Integration tests start their own Postgres and Keycloak containers (Testcontainers), so Docker
must be running. The login tests drive the real Keycloak login and registration pages.

## Production

See `docker-compose.yml`, `Caddyfile`, and `.env.example`: copy `.env.example` to `.env`, fill
in every value, and run `docker compose up -d`. Hosting guides follow in `docs/`.
