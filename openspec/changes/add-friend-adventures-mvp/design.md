## Context

Adventr is a greenfield project: the repository contains only docs. The product is a private, shared bucket list for friend groups. The hard constraints are:

- Everything is free: open-source software and free hosting.
- Every friend has a real account.
- The stack uses Java and Keycloak.

Expected load is tiny: a few groups of 3–20 people. The design optimizes for simplicity, a small operational footprint on one free VM, and security of private group data.

## Goals / Non-Goals

**Goals:**
- Adding an idea takes about 5 seconds: a title-only form, reachable from the list.
- One obvious, central place where group membership is enforced.
- Server-rendered UI with small htmx swaps. No SPA build toolchain.
- Reproducible deployment with a single `docker compose up -d`, including the Keycloak realm.
- Data survives the loss of the VM, through off-host backups.

**Non-Goals:**
- Admin role, maps/geocoding, app notifications/email, public groups, threaded comments, and multiple images per adventure.
- Horizontal scaling or high availability.
- A public JSON API or mobile app.

## Decisions

### 1. Keycloak for identity only; authorization data in the app DB
Keycloak owns credentials, registration, password reset, and Google login. Groups, memberships, roles, and invites live in Postgres tables owned by the app.
- *Why*: Per-group roles for user-created groups are dynamic domain data. Modeling them as Keycloak groups would need admin-API calls, duplicate state, and make queries awkward.
- *Alternative*: Keycloak groups plus the admin API. Rejected as too coupled and too complex.

On every authenticated request, a filter or `OidcUserService` hook upserts `users(keycloak_sub, display_name, email)`. The display name comes from `preferred_username`, or from `given_name`/`family_name` when present, and is refreshed at each login.

### 2. Spring Boot 3 + Thymeleaf + htmx
- Spring Security `oauth2Login()` with Keycloak as the OIDC provider. Sessions are server-side (the default `HttpSession`), and CSRF protection stays enabled. htmx sends the CSRF token through an `hx-headers` attribute on `<body>`.
- Reactions and comments are htmx endpoints that return Thymeleaf fragments, e.g. the reaction bar fragment or the comment list fragment. Every page also works as a full-page render.
- *Alternative*: React SPA + REST. Rejected: it adds a second toolchain and token handling in the browser for no MVP benefit.

### 3. Central authorization: `GroupAccessService`
Every group-scoped service method starts with `GroupAccessService.requireMember(userId, groupId)`, which returns the `Membership` (including its role), or `requireOwner(...)`.
- Controllers never check membership themselves. Service methods take `groupId` explicitly, and child entities (adventure, comment, image) are always loaded with a `groupId` predicate, e.g. `findByIdAndGroupId`. This prevents cross-group ID tampering.
- If the user is not a member, the service throws `GroupAccessDeniedException`, which is mapped to **404**. A 404 avoids revealing that the group exists.
- An ArchUnit test (or a simple convention test) asserts that public methods of group-scoped services call the guard, or are annotated to show they are exempt.

### 4. Data model (Flyway-managed)
```
users(id, keycloak_sub UNIQUE, display_name, email, created_at, deleted_at NULL)
groups(id, name, created_at)
memberships(id, group_id, user_id, role OWNER|MEMBER, joined_at, left_at NULL,
            UNIQUE(group_id,user_id))
invites(id, group_id UNIQUE, token UNIQUE, expires_at, created_by, created_at)
adventures(id, group_id, created_by, title, description, location,
           date_from, date_to, time_hint, cost_amount NUMERIC, cost_note,
           link, image_path, status IDEA|PLANNED|DONE, status_changed_at,
           created_at, updated_at)
reactions(adventure_id, user_id, type UP|DOWN, created_at,
          PRIMARY KEY(adventure_id,user_id))
comments(id, adventure_id, author_id, text, created_at, edited_at NULL)
```
- A member who leaves or is removed keeps their `memberships` row, with `left_at` set. Active membership means `left_at IS NULL`. Content references `users`, so the author's name still resolves and is shown with a "former member" label.
- There is one active invite per group (`UNIQUE(group_id)`). Regenerating the link replaces the token and the expiry.
- Tokens are 32 random bytes from `SecureRandom`, base64url-encoded.
- The default invite expiry is **7 days**.

### 5. Ownership rules (defaults chosen)
- There is exactly one owner per group.
- The owner **cannot leave** while other active members exist. They must transfer ownership first, and the UI prompts them to do so.
- If the owner is the last active member, leaving deletes the group and all of its content.
- Auto-promotion of the longest-standing member, ordered by `joined_at`, happens only when the owner's account is deleted.

### 6. Adventure list ordering
The list is split into three sections, in this order:
1. **Planned**: sorted by `date_from` ascending, nulls last, then net score descending, then `created_at` descending.
2. **Ideas**: sorted by net score (`ups - downs`) descending, then `created_at` descending.
3. **Memories (Done)**: sorted by `status_changed_at` descending.

Counts come from one aggregate query (`LEFT JOIN reactions GROUP BY adventure`), plus the current user's own reaction and the comment count. This avoids N+1 queries.

### 7. Reactions as upsert/delete
`ReactionService.toggle(user, adventure, clicked)` reads the existing row and then:
- deletes it if the clicked type equals the existing type,
- updates it if the types differ,
- inserts it if there is no row.

The primary key guarantees uniqueness. A concurrent double click that hits the constraint is retried once.

### 8. Images: disk storage, served through the app
- Uploads are limited to 10 MB and must be JPEG, PNG, or WebP, checked by magic bytes and not only by content type.
- Each upload is resized to a maximum of 1600 px on its longer side, EXIF orientation is applied, metadata is stripped, and the result is re-encoded as JPEG (quality about 0.8) under `/data/images/{groupId}/{uuid}.jpg`.
- Images are served **only** through `GET /groups/{gid}/adventures/{aid}/image`, which calls the membership guard. Caddy never serves the images directory. The response uses `Cache-Control: private`.
- Replacing or removing an image deletes the old file.

### 9. Deployment topology
```
Internet ─▶ Caddy :443 ─┬─ /            ─▶ app:8080
                        └─ /auth/*      ─▶ keycloak:8080 (KC_HTTP_RELATIVE_PATH=/auth)
            postgres:5432  (databases: adventr, keycloak; not exposed)
            volumes: pgdata, images, caddy_data, backups
```
- **Path-based Keycloak** gives one hostname and one certificate, and works the same way behind a Cloudflare Tunnel.
- Keycloak runs in production mode (`start --optimized` with a custom build) or `start`, with `KC_HOSTNAME=https://<host>/auth`, `KC_PROXY_HEADERS=xforwarded`, and `KC_HTTP_ENABLED=true` behind Caddy.
- The realm (`adventr`, self-registration on, reset-password on, `adventr-app` confidential client, optional Google IdP, SMTP settings) is imported from `keycloak/realm-adventr.json` at startup via `--import-realm`. Secrets are injected from `.env`.
- All images are multi-arch (ARM64-compatible) for the Oracle Ampere VM and the Raspberry Pi.
- The Pi variant replaces Caddy's public HTTPS with `cloudflared`, which terminates TLS at Cloudflare. It uses a Compose profile or an override file.

### 10. Email
Keycloak is configured with a free SMTP relay (e.g. Brevo free tier or a Gmail app password) **only** for password reset. Email verification is off for the MVP. The app sends no email.

### 11. Backups
A small `backup` container (or a host cron job) runs nightly:
- `pg_dump` of both databases,
- a tar of the images volume,
- rotation that keeps 7 daily and 4 weekly backups,
- a copy off the VM with `rclone` to a free remote (e.g. Oracle Object Storage free tier or Google Drive).

A documented restore procedure is part of the deliverables.

## Risks / Trade-offs

- **Oracle reclaims idle Always Free instances** (low CPU, network, and memory over 7 days) → Upgrade the account to Pay-As-You-Go, which still costs nothing within the free limits. Document the Pi fallback, and keep backups off the VM.
- **Oracle ARM capacity is often unavailable** at provisioning time → Retry or try another availability domain. The Pi is the fallback.
- **Keycloak memory use (~512 MB–1 GB)** → Set `JAVA_OPTS_KC_HEAP` limits. The Oracle VM has plenty of RAM, and a Pi with 8 GB is fine.
- **Keycloak proxy/hostname misconfiguration breaks redirects** → Pin the hostname settings in `.env` and add a smoke-test task: log in through the public URL.
- **Invite link leakage** (links are reusable and shared in chats) → Expiry and regeneration are the mitigation. Accepted for the MVP.
- **Single VM with no high availability** → Accepted. Backups plus a documented restore cover it.
- **The free SMTP relay may throttle or change its terms** → Password reset is the only dependency. The SMTP settings are swappable in the realm or `.env`.
- **Local disk for images** → Simple, but tied to the host. Images are included in backups.

## Migration Plan

This is a greenfield first deploy:
1. Provision the VM or Pi.
2. Point DuckDNS or the tunnel at it.
3. Copy the compose files and `.env`.
4. Run `docker compose up -d`.
5. Verify the smoke test.

Rollback means redeploying the previous app image tag. Flyway migrations are forward-only, and a restore from backup covers catastrophic cases.

## Open Questions

- Google login: include it in the first deploy, or add it later? The realm config supports both. It is off by default until a Google OAuth client is created.
- Which off-host backup target to use: Oracle Object Storage or Google Drive via `rclone`. This can be decided at deploy time.
