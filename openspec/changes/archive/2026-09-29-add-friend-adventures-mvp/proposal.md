## Why

Friends collect "we should do this together" ideas in chat threads, where they get buried and never happen. Adventr gives each friend group a private, shared bucket list where ideas can be added in seconds, voted on, discussed, planned, and remembered, and it runs entirely on free, open-source software and free hosting.

## What Changes

- New Spring Boot 4 / Java 21 web application (Thymeleaf + htmx) backed by PostgreSQL. The repository is currently empty.
- Keycloak handles identity only: login, self-registration, password reset (via a free SMTP relay), and optional Google login. The app mirrors each Keycloak user into a local `User` row keyed by `sub` on first login.
- Private groups with Owner and Member roles. A user can create groups and belong to many groups. A single, central membership check in the service layer protects all group-scoped data, including uploaded images.
- Reusable, expiring invite links (`/join/{token}`) that the owner can regenerate. Regenerating invalidates the previous link.
- Per-group adventure list and detail pages. Only the title is required. Adventures have a status (IDEA → PLANNED → DONE) and an optional single uploaded image.
- 👍/👎 reactions (one per user per adventure, toggle semantics) with visible counts and reactor names. The list is sorted by net score.
- A flat comment thread per adventure. Authors can edit and delete their own comments, and the owner can delete any comment.
- Docker Compose deployment (app, Keycloak, Postgres, Caddy with automatic HTTPS) on Oracle Cloud Always Free, with Raspberry Pi + Cloudflare Tunnel as the alternative, plus nightly backups.
- Out of scope: Admin role, maps/geocoding, app notifications/email, public groups, threaded comments, multiple images.

## Capabilities

### New Capabilities
- `user-auth`: Keycloak OIDC login/registration/password reset, local user provisioning by `sub`, logout, and account display names.
- `groups`: Group creation, the "my groups" landing page, group switcher, memberships, Owner/Member roles, leaving/removal, ownership transfer, group deletion, and the central membership authorization rule.
- `invites`: Reusable, expiring invite links, regeneration, and the `/join/{token}` flow through Keycloak login/registration.
- `adventures`: Adventure CRUD, fields, status lifecycle, list ordering and sections, detail page, and single image upload/serving.
- `reactions`: 👍/👎 toggle semantics, uniqueness, counts, net-score sorting, and reactor names.
- `comments`: Flat comment thread, edit/delete permissions, and the "(edited)" marker.
- `deployment`: Docker Compose stack, Keycloak realm provisioning, reverse proxy/HTTPS, hosting targets, configuration, and backups.

### Modified Capabilities
<!-- None: no existing specs. -->

## Impact

- **Code**: New Maven project (Spring Boot 4, Spring Security OAuth2 Client, Spring Data JPA, Flyway, Thymeleaf, htmx via WebJar or static asset, Thumbnailator or equivalent for image resizing).
- **Systems**: Keycloak (one realm, one confidential client), PostgreSQL with two databases (`adventr`, `keycloak`), Caddy, and a Docker volume for images and backups.
- **External free services**: Oracle Cloud Always Free (or a self-hosted Pi), DuckDNS or a Cloudflare Tunnel hostname, a free SMTP relay for Keycloak password-reset mail, and optionally a Google OAuth client.
- **Data**: New schema: `users`, `groups`, `memberships`, `invites`, `adventures`, `reactions`, `comments`.
