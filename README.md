# Adventr

A project for me and my friends: a private, shared bucket list for friend groups.

Friends collect "we should do this together" ideas in chat threads, where they get buried and
never happen. Adventr gives each friend group a private list where ideas can be added in
seconds, voted on, discussed, planned, and remembered.

It is **free for everyone**: no subscriptions, no payments, and no paid tier. It runs entirely
on free, open-source software and free hosting.

Spring Boot 4 (Java 21) · Thymeleaf + htmx · PostgreSQL · Keycloak · Docker Compose + Caddy.

## Features and status

| Feature | Status |
|---|---|
| Accounts: login, self-registration, and password reset through Keycloak | ✅ Done |
| Private groups with Owner and Member roles, group switcher, and settings | ✅ Done |
| Leaving, removing members, ownership transfer, and deleting a group | ✅ Done |
| Reusable, expiring invite links (`/join/{token}`) that the owner can regenerate | ✅ Done |
| Adventures: list with quick add, detail page, IDEA → PLANNED → DONE status, and one image | ✅ Done |
| 👍/👎 reactions with counts and names, sorted by net score | ✅ Done |
| Comment thread per adventure | ✅ Done |
| Nightly off-host backups, deployment guides (Oracle VM, Raspberry Pi + Cloudflare Tunnel) | ✅ Done |
| Go-live: SMTP, Google login, production smoke test, and a tested restore | 🚧 Operator tasks (MVP 9.2, 9.6–9.9) |
| My account page: display name, bio, avatar, light/dark theme, and primary color | ✅ Done (browser accessibility walkthrough pending) |

Groups are always private. The only way into a group is an invite link, and every
group-scoped request goes through one central membership check in the service layer.

## Architecture

```
               Internet (80/443)
                      │
               ┌──────▼──────┐
               │    Caddy    │  automatic HTTPS
               └──┬───────┬──┘
          /auth/* │       │ /*
          ┌───────▼──┐  ┌─▼──────────────────┐
          │ Keycloak │  │  App (Spring Boot) │──── images volume
          │ identity │  │  Thymeleaf + htmx  │     (served only through the app)
          └────┬─────┘  └─┬──────────────────┘
               │          │
          ┌────▼──────────▼────┐
          │     PostgreSQL     │  databases: keycloak, adventr
          └────────────────────┘
```

- **Keycloak handles identity only:** login, registration, password reset, and optional Google
  login. On each login, the app mirrors the user into a local `users` row, keyed by the
  Keycloak `sub`.
- **The app owns everything else:** groups, memberships, roles, invites, and adventures.
  The UI is server-rendered, with htmx fragment swaps.
- **Only Caddy publishes ports.** Postgres, Keycloak, and the app are reachable only inside
  the Compose network.

## Project structure

| Path | What it holds |
|---|---|
| `src/main/java/app/adventr/` | The app, one package per capability: `config`, `user`, `group`, `invite`, `adventure` (with reactions and comments), `image`, `account`, `web` |
| `src/main/resources/templates/` | Thymeleaf pages and fragments (`layout.html` is the base layout) |
| `src/main/resources/db/migration/` | Flyway migrations (`V1__users.sql`, …) |
| `src/main/resources/static/css/app.css` | The stylesheet |
| `src/test/java/app/adventr/` | Unit, integration (Testcontainers), and ArchUnit tests |
| `keycloak/realm-adventr.json` | The Keycloak realm, imported on the first start |
| `postgres/init-databases.sh` | Creates the `adventr` and `keycloak` databases and users |
| `docker-compose.yml`, `Caddyfile`, `Dockerfile` | The production stack |
| `docker-compose.tunnel.yml`, `Caddyfile.tunnel` | The Raspberry Pi variant behind a Cloudflare Tunnel |
| `backup/` | The nightly backup service (`pg_dump`, images archive, rclone) |
| `docs/` | Deployment guides, the restore procedure, and the accessibility audit |
| `docker-compose.dev.yml` | The local dev stack (Postgres, Keycloak, Mailpit) |
| `openspec/` | Specifications and change proposals (see [Specs](#specs)) |

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

To try an invite flow locally, use a second browser profile or a private window for the second
account.

### Tests

```sh
./mvnw verify
```

Integration tests start their own Postgres and Keycloak containers (Testcontainers), so Docker
must be running. The login tests drive the real Keycloak login and registration pages.
ArchUnit tests enforce the architecture rules: group-scoped services must call the membership
guard, and controllers must not use repositories directly.

## Production

The production stack is `docker-compose.yml`: Caddy, the app, Keycloak, Postgres, and a
nightly backup service. It targets a single free VM (Oracle Cloud Always Free, ARM) or a
Raspberry Pi. All images are multi-arch (amd64 and arm64). Step-by-step guides:

- [docs/deploy-oracle.md](docs/deploy-oracle.md): Oracle Cloud VM with DuckDNS, including SMTP, Google login, and go-live checks
- [docs/deploy-pi.md](docs/deploy-pi.md): Raspberry Pi behind a Cloudflare Tunnel (`docker-compose.tunnel.yml`)
- [docs/restore.md](docs/restore.md): backups, the off-host copy, and restoring on a new host

In short:

1. Point a hostname at the server, e.g. a free DuckDNS subdomain, and open ports 80 and 443.
2. Copy the environment template and fill in every value:

   ```sh
   cp .env.example .env
   ```

3. Build and start the stack:

   ```sh
   docker compose up -d --build
   ```

4. Open `https://<APP_HOST>` and register the first account.

Caddy gets a certificate automatically. Keycloak is at `https://<APP_HOST>/auth`, and its
admin console is at `https://<APP_HOST>/auth/admin`.

### Configuration

All settings live in `.env`. **Never commit `.env`**; it is ignored by Git.

| Variable | Purpose |
|---|---|
| `APP_HOST` | Public hostname without a scheme, e.g. `adventr.duckdns.org` |
| `POSTGRES_PASSWORD` | Postgres superuser (admin tasks and backups only) |
| `APP_DB_PASSWORD` | Owner of the `adventr` database, used by the app |
| `KEYCLOAK_DB_PASSWORD` | Owner of the `keycloak` database, used by Keycloak |
| `KEYCLOAK_ADMIN_USERNAME`, `KEYCLOAK_ADMIN_PASSWORD` | Keycloak bootstrap admin |
| `KEYCLOAK_CLIENT_SECRET` | Secret of the `adventr-app` client, shared by Keycloak and the app |
| `KEYCLOAK_JAVA_OPTS_HEAP` | Keycloak heap limits |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM`, `SMTP_USER`, `SMTP_PASSWORD` | A free SMTP relay, used only for password-reset emails |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Optional Google login (see below) |
| `APP_VERSION` | Image tag for the app service |
| `APP_JAVA_OPTS` | JVM options for the app |
| `BACKUP_TIME`, `TZ` | Time of the nightly backup (`HH:MM`) and its time zone |
| `BACKUP_RCLONE_REMOTE` | rclone remote for the off-host backup copy (see [docs/restore.md](docs/restore.md)) |
| `CLOUDFLARE_TUNNEL_TOKEN` | Raspberry Pi variant only: the Cloudflare Tunnel token |

Generate secrets with e.g. `openssl rand -base64 32 | tr -d '/+=' | cut -c1-32`.

The realm settings in `realm-adventr.json` are imported only on the very first start. If you
change a secret later, e.g. `KEYCLOAK_CLIENT_SECRET`, update it in the Keycloak admin console
as well.

### Google login (optional)

The realm contains a Google identity provider that is disabled by default. To enable it:

1. In the Google Cloud Console, create an OAuth client (type "Web application") with the
   redirect URI `https://<APP_HOST>/auth/realms/adventr/broker/google/endpoint`.
2. Put the client ID and secret into `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`.
3. In the Keycloak admin console, go to **Identity providers > google**, check the
   credentials, and enable it.
4. Publish the OAuth consent screen. In testing mode, only listed test users can sign in. The
   basic `openid email profile` scopes need no Google review.

Google login is free and needs no app code.

## Specs

The project is specified with [OpenSpec](https://github.com/Fission-AI/OpenSpec). The current
requirements, with WHEN/THEN scenarios, live in `openspec/specs/` (one folder per capability:
`user-auth`, `groups`, `invites`, `adventures`, `reactions`, `comments`, `deployment`). Each change
has a proposal, a design, delta specs, and a task list:

| Change | What it covers |
|---|---|
| `openspec/changes/archive/2026-09-29-add-friend-adventures-mvp/` | The MVP (archived): auth, groups, invites, adventures, reactions, comments, and deployment. Go-live tasks 9.2 and 9.6–9.9 remain for the operator. |
| `openspec/changes/account-and-appearance/` | Follow-up: My account page, avatar, themes, primary color, and a contrast audit |

Small implementation decisions are recorded under "Implementation Notes" in each change's
`design.md`.
