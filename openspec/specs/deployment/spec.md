# deployment Specification

## Purpose
TBD - created by archiving change add-friend-adventures-mvp. Update Purpose after archive.
## Requirements
### Requirement: Docker Compose stack
The system SHALL be deployable with a single `docker compose up -d` that starts the app, Keycloak, PostgreSQL, and Caddy. All images SHALL support linux/arm64 and linux/amd64. All configuration and secrets SHALL come from an `.env` file, which SHALL NOT be committed. A committed `.env.example` SHALL list every variable.

#### Scenario: Fresh start
- **WHEN** an operator copies `.env.example` to `.env`, fills in the values, and runs `docker compose up -d` on an empty ARM64 host
- **THEN** all services become healthy and the app is reachable over HTTPS at the configured hostname

### Requirement: Free and open-source only
Every runtime component and hosting dependency SHALL be free of charge and open source, or on a free tier with no mandatory payment.

#### Scenario: Dependency review
- **WHEN** a new dependency or service is proposed
- **THEN** it is accepted only if it has an OSI-approved license or a free tier that covers the app's needs

### Requirement: Database separation
PostgreSQL SHALL host two separate databases, one for the app and one for Keycloak, each with its own credentials. PostgreSQL SHALL NOT be exposed outside the Docker network. The app schema SHALL be managed by versioned Flyway migrations.

#### Scenario: Port exposure
- **WHEN** someone scans the host from the internet
- **THEN** only ports 80 and 443 (Caddy) are reachable, or none at all when running behind a Cloudflare Tunnel

### Requirement: Reproducible Keycloak realm
The `adventr` realm SHALL be imported automatically at startup from a version-controlled realm file. The realm SHALL have self-registration, "forgot password", and SMTP settings configured, a confidential `adventr-app` client whose redirect URIs are limited to the app hostname, and an optional Google identity provider. Secrets SHALL be injected from environment variables.

#### Scenario: Realm present after first boot
- **WHEN** Keycloak starts on an empty database
- **THEN** the `adventr` realm exists with the client, registration, and reset-password settings, and no manual console steps are needed

### Requirement: Reverse proxy and HTTPS
Caddy SHALL terminate TLS with automatically obtained certificates, route `/auth/*` to Keycloak and everything else to the app, and forward the `X-Forwarded-*` headers. Keycloak SHALL be configured with its public hostname and proxy-header mode so that redirects use the public HTTPS URL. HTTP SHALL redirect to HTTPS.

#### Scenario: End-to-end login through the proxy
- **WHEN** a user opens `https://<host>/` and logs in
- **THEN** every redirect between the app and Keycloak stays on `https://<host>` and login succeeds

### Requirement: Hosting targets
The deployment SHALL be documented and working for two targets:
- **Primary**: Oracle Cloud Always Free (Ampere ARM VM) with a DuckDNS hostname and Caddy HTTPS.
- **Alternative**: Raspberry Pi 4/5 (8 GB) behind a Cloudflare Tunnel, with no open inbound ports.

#### Scenario: Pi deployment via tunnel
- **WHEN** an operator starts the stack with the Cloudflare Tunnel profile or override on a Pi
- **THEN** the app is reachable at the tunnel hostname over HTTPS without port forwarding

### Requirement: Resource limits
The Keycloak and app JVMs SHALL have explicit heap limits, so that the stack runs within the RAM of the chosen host, with Keycloak at about 512 MB–1 GB.

#### Scenario: Memory stays bounded
- **WHEN** the stack runs idle for 24 hours on the target host
- **THEN** no container is killed for running out of memory

### Requirement: Persistent data
Database data, uploaded images, and Caddy certificates SHALL be stored in named Docker volumes that survive container re-creation.

#### Scenario: Upgrade without data loss
- **WHEN** the operator pulls a new app image and runs `docker compose up -d`
- **THEN** all groups, adventures, and images are still present

### Requirement: Nightly off-host backups
The system SHALL back up both databases and the images volume nightly. It SHALL keep 7 daily and 4 weekly backups and copy them to a free storage location off the host. A documented restore procedure SHALL exist and SHALL have been tested at least once.

#### Scenario: Restore after host loss
- **WHEN** the host is lost and the operator follows the restore procedure on a new host using the latest off-host backup
- **THEN** users, groups, adventures, reactions, comments, images, and Keycloak accounts are restored

### Requirement: Health checks
The app SHALL expose a health endpoint (Spring Boot Actuator `health`, reachable only inside the Docker network), and Compose SHALL use health checks to order startup: Postgres first, then Keycloak, then the app.

#### Scenario: Startup ordering
- **WHEN** the stack starts from cold
- **THEN** the app starts only after Keycloak reports healthy, and does not fail on OIDC discovery

