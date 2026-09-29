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

### 2. Spring Boot 4 + Thymeleaf + htmx
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

- ~~Google login: include it in the first deploy, or add it later?~~ **Resolved (2026-09-28):** Enable it at the first deploy (task 9.9). It needs a real hostname for the redirect URI, and it makes joining via an invite link easier for friends who don't want another password. It needs no app code.
- Which off-host backup target to use: Oracle Object Storage or Google Drive via `rclone`. This can be decided at deploy time.

## Implementation Notes

Small decisions and deviations made during implementation (task groups 1–3).

- **Spring Boot 4.1 instead of 3.** Spring Initializr no longer offers 3.x, and the 3.5 line is out of free OSS support. Boot 4.1.1 (Spring Security 7, Testcontainers 2) runs on Java 21 and changes nothing in the architecture. Decided with the project owner on 2026-09-28.
- **htmx 2.0.x, not 4.x.** htmx 4 makes attribute inheritance opt-in, which would break the `hx-headers` CSRF attribute on `<body>` (decision 2). The WebJar is pinned to 2.0.11 and served via `webjars-locator-lite` at `/webjars/htmx.org/dist/htmx.min.js`.
- **Layout.** A fragment-based layout (`layout.html` with `page(title, content)`) instead of the Thymeleaf Layout Dialect, which avoids an extra dependency.
- **Display name.** "given_name family_name" when either is set, otherwise `preferred_username`, then email, then `sub`.
- **User provisioning.** A custom `OidcUserService` upserts the user at login with `INSERT … ON CONFLICT (keycloak_sub) DO UPDATE`, so concurrent first requests cannot create duplicates. `CurrentUser` is resolved per request by `sub`, with a provisioning fallback.
- **Public paths.** `/`, `/error`, `/css/**`, `/webjars/**`, `/favicon.ico`, and `/actuator/health/**`. Caddy answers 404 for `/actuator/*`, so health is reachable only inside the Docker network.
- **Realm placeholders.** `realm-adventr.json` uses Keycloak's `${ENV_VAR:default}` substitution. The SMTP sender has a syntactically valid default, because Keycloak refuses to import a realm with an empty sender address. `SMTP_AUTH` and `SMTP_STARTTLS` are placeholders too, so the dev stack can use Mailpit.
- **Realm import runs only once** (`IGNORE_EXISTING`). Later changes to the file or to secrets such as `KEYCLOAK_CLIENT_SECRET` must also be made in the admin console, or applied by re-creating the Keycloak database.
- **Realm hardening defaults.** PKCE (S256) is required for `adventr-app`, brute-force protection is on, and the password policy is `length(8) and notUsername`.
- **Container-to-public-host traffic.** Caddy has a Compose network alias equal to `APP_HOST`. The app's OIDC discovery and token calls to `https://<APP_HOST>/auth/...` therefore go straight to Caddy (with its real certificate) instead of hairpinning through the host's public IP. The app therefore `depends_on` Caddy (started) in addition to Keycloak (healthy).
- **Keycloak start mode.** It uses `start --import-realm` (not `--optimized`), so Keycloak re-runs its build step on each start, which adds some startup time. This is acceptable for the MVP and avoids a custom image.
- **Health checks.** Neither the Keycloak nor the Temurin image has curl, so the checks speak HTTP over bash's `/dev/tcp`. Keycloak's check uses management port 9000 (`/auth/health/ready`) and the app's uses `/actuator/health/readiness`.
- **Dev setup.** `docker-compose.dev.yml` runs Postgres (:5432), Keycloak (:8081/auth), and Mailpit (:8025). The app runs from the IDE with the `dev` profile (`application-dev.yml` holds the throwaway dev credentials).
- **Tests.** Integration tests share one context with Postgres and Keycloak containers, and Keycloak imports the production realm file. The app listens on a pre-chosen free port, because the realm only allows redirect URIs under `APP_BASE_URL`. The end-to-end login tests use a small scripted HTTP client (`Browser`): Keycloak sets `Secure` cookies even on http://localhost, which browsers accept but `java.net.CookieManager` does not send.
- **Keycloak admin console.** It is reachable at `https://<APP_HOST>/auth/admin` and protected only by the bootstrap admin password. Restricting it (e.g. with a Caddy IP allowlist) is a candidate follow-up.

Small decisions made during task groups 4–5 (groups and invites).

- **Guard convention.** Group-scoped services are annotated `@GroupScopedService`. `ArchitectureTests` checks that each of their public methods reaches `GroupAccessService.requireMember`/`requireOwner`, directly or through a method of the same class, or carries `@GuardExempt("reason")`. The exempt methods are "create group", "my groups", and the invite-token `preview`/`join`, where the token is the authorization. A second rule forbids `@Controller`/`@ControllerAdvice` classes from depending on Spring Data repositories.
- **Error mapping.** `GroupAccessDeniedException` → 404 and `ForbiddenActionException` → 403 via `@ResponseStatus`, rendered by the existing `error.html`. Rule refusals that a permitted user can hit (the Owner leaving while others remain, a wrong delete confirmation, removing yourself, transferring to someone who has left, an invalid name) throw `GroupRuleException`. They are shown as a flash message on the settings page, not as an error page.
- **One active owner, enforced in the database.** A partial unique index `memberships(group_id) where role = 'OWNER' and left_at is null`. Transfer demotes and flushes before promoting. A leaving or removed member's row is kept with `left_at` set and its role reset to `MEMBER`.
- **Entity name.** `Group` is mapped as the JPA entity `FriendGroup`, because `GROUP` is reserved in JPQL. The table is still `groups`.
- **Group deletion cascade.** Rows go through `on delete cascade` foreign keys (memberships and invites now, adventures/reactions/comments from V4–V6). `GroupDeletion` then publishes `GroupDeletedEvent(groupId)`. Task 6.7's image storage listens with `@TransactionalEventListener(AFTER_COMMIT)` and removes `/data/images/{groupId}`.
- **Owner succession (4.9).** `OwnerSuccessionService.accountDeleted(userId)` promotes the earliest-joined remaining member (ties by membership id), or deletes the group if nobody is left. It then ends all of the user's memberships. The MVP has no account-deletion trigger yet: Keycloak deletions are not propagated to the app. The service is ready for one, e.g. an admin action or a Keycloak event listener.
- **Former-member label (4.10).** `AuthorLabels.authorsIn(groupId, userIds)` returns `Author(displayName, formerMember)` with two queries per page. A user counts as a former member when they have no active membership or `deleted_at` is set. Templates render it with `fragments/author :: author(${author})`.
- **Group page placeholder.** `/groups/{id}` shows the group name and a placeholder until the adventure list (task 6.5) replaces it. Settings live at `/groups/{id}/settings`.
- **Group switcher.** A `<details>` menu of links in the header, so it needs no JavaScript. It is shown on every logged-in page when the user has at least one group, not only inside groups. The current group comes from the `{groupId}` URI variable.
- **Confirmations.** Remove, make-owner, leave, and regenerate use a static `confirm()` on the form. Delete requires typing the exact group name (case-sensitive, surrounding whitespace ignored).
- **Join flow (5.4).** `GET /join/{token}` never changes data, so chat link-preview bots cannot join anyone. It shows the group name and a "Join group" button, and the POST joins. This also applies to a new user returning from Keycloak registration: they land on the join page and are one click from the group. Active members (including the Owner) are redirected straight to the group. An invalid or regenerated token answers 404 and an expired one 410; neither reveals the group name. Joining is a single `insert … on conflict (group_id, user_id) do update … where left_at is not null`, which also handles rejoining with a fresh `joined_at`.
- **Invites.** 32 `SecureRandom` bytes, base64url without padding (43 characters), valid for 7 days. `insert … on conflict (group_id) do update` replaces token and expiry in one statement. The shareable URL is built from the current request (`ServletUriComponentsBuilder`), so behind Caddy it is the public https URL. An expired link stays visible on the settings page as "expired", with a button for the Owner to create a new one.
- **Time.** A shared `Clock` bean (`TimeConfig`) supplies "now", and its zone is used for display. The JVM default zone is used, which is UTC in the container unless `TZ` is set.

Small decisions made during task groups 6–8 (adventures, reactions, comments).

- **One `adventure` package.** Adventures, reactions, and comments share `app.adventr.adventure`, the same way `group` holds memberships. The list query aggregates all three tables, and the detail page shows all three, so separate packages would depend on each other in a cycle. Each capability still has its own guarded service (`AdventureService`, `ReactionService`, `CommentService`) and controller.
- **Group page.** `GET /groups/{id}` moved from `GroupController` to `AdventureController`, and the placeholder template is gone. `group` does not depend on `adventure`.
- **Migrations V4–V6 written together.** The list query (6.4) needs the `reactions` and `comments` tables, so all three migrations were added at the start of group 6. Check constraints mirror the validation: status values, `date_to >= date_from`, `cost_amount >= 0`, and reaction type.
- **Validation.** The form binds to `AdventureForm` (raw strings), and `AdventureFields.validate` collects every field error at once, so an unparsable date or amount is a field message rather than a binding failure. Text is trimmed, `\r\n` is normalized to `\n` before counting length, and blank optional fields become `null`. Links must parse as an absolute `http`/`https` URI with a host. Cost accepts `,` or `.` as the decimal separator, with at most two decimals, below 100 million (`numeric(10, 2)`).
- **Permissions.** Only the creator edits an adventure or changes its image. The creator or the Owner deletes. Any member changes the status. `status_changed_at` changes only when the status actually changes. `updated_at` ("last updated") tracks edits to fields and the image, not status changes. An adventure or comment id from another group or adventure answers 404 (`AdventureNotFoundException`).
- **List query.** One native query through `JdbcClient` (`AdventureQueries`): up/down counts, the viewer's reaction, comment count, and reactor ids (`array_agg … filter`). A single `order by` produces all three sections, using section-specific sort keys that are `null` outside their section. Reactor names for the tooltips come from one `AuthorLabels` call, so the page needs three queries in total.
- **Quick add.** `hx-post` re-renders `#adventure-sections` and sends a fresh form out of band (`hx-swap-oob`) with `autofocus`, so the next idea can be typed right away. A blank title answers with `HX-Retarget: #quick-add` and the error in the form. Without JavaScript it is a normal post and redirect. "Add with details" opens the full form (`/adventures/new`).
- **Image pipeline (6.7).**
  - `app.adventr.image.ImageProcessor` knows nothing about storage, so the avatar work in `account-and-appearance` can reuse it.
  - The type is checked by magic bytes, and the matching ImageIO reader is chosen by that type.
  - Images over 100 megapixels are refused before decoding. Large images are subsampled while decoding, to at least twice the target edge, so memory stays bounded under the 384 MB heap.
  - EXIF orientation is read with metadata-extractor and applied with an affine transform. Transparent areas become white, and the image is scaled with Thumbnailator and re-encoded as a quality-0.8 JPEG from pixels only, so no metadata survives. Smaller images are not scaled up.
  - Dependencies: Thumbnailator (MIT), metadata-extractor (Apache 2.0), and TwelveMonkeys `imageio-webp`/`imageio-jpeg` (BSD-3). The last also reads CMYK JPEGs, which plain ImageIO can't.
- **Image storage.** `ImageStore` writes under `adventr.images.dir` (`/data/images`, or `target/dev-images` in the dev profile). A file written in a transaction that rolls back is removed again. Deletions run after commit. Each adventure has `{groupId}/{uuid}.jpg` (at most 1600 px) plus `{uuid}-thumb.jpg` (320 px) for the list. The group's `GroupDeletedEvent` listener removes `{groupId}/` directly, because it already runs after commit.
- **Image URLs.** `…/image?v={uuid}` and `…/thumbnail?v={uuid}` are served with `Cache-Control: private, max-age=31536000, immutable`. The version changes with every upload, so a cached image never goes stale.
- **Upload limits.** The app enforces 10 MB with a clear message. Spring's multipart limit is 25 MB (and Tomcat's swallow size 30 MB), so a 15 MB file still reaches that message. A request above 25 MB fails while Spring Security's CSRF filter reads the parameters, and gets the generic 403 page. That is accepted for such an unlikely upload.
- **Reactions.** `ReactionService.toggle` follows decision 7 literally: read, then delete, update, or insert in a `TransactionTemplate`. A `DuplicateKeyException` from a concurrent double click retries once in a fresh transaction, which then toggles from the other click's row. Switching type resets `created_at`, so name lists show when someone reacted that way.
- **Reaction bar.** One fragment, `bar(bar, detail)`. On the detail page, the swapped element also holds the 👍/👎 name lists, so they update with the counts. The own reaction is marked by `aria-pressed`, bold text, and an outline, not by color alone. Buttons keep stable ids, so htmx restores keyboard focus after the swap. Without JavaScript, the form posts `view=list|detail` and redirects back (to `#reactions-{id}` in the list).
- **Comments.** Posting and deleting re-render the whole `#comments` section, because the count in the heading changes. Editing swaps one `<li>`. Without JavaScript, "Edit" opens the detail page with `?editComment={id}`, and only the author's own comment turns into a form. Deleting asks with `hx-confirm`, so there is no confirmation without JavaScript. Owners see "Delete" on every comment, and "Edit" only on their own.

Small decisions made during task group 9 (deployment).

- **Backup service.** A `backup` Compose service is built from `postgres:17-alpine`, so `pg_dump` matches the server's major version, plus `rclone`. It runs a sleep-until-`BACKUP_TIME` loop instead of `crond`, so the job keeps the container's environment. Arguments run as a command instead of the loop (`docker compose run --rm backup rclone config`).
- **Backup format.** `pg_dump --format=custom` for both databases and a `tar.gz` of the images volume. Each run writes to `daily/<date>.partial` and is renamed only when complete. Sundays are also copied to `weekly/`. The newest 7 daily and 4 weekly backups are kept.
- **Off-host copy.** `rclone copy`, then an age-based `rclone delete --min-age` on the remote (8 days daily, 29 days weekly). It is never `rclone sync`, which would let a rebuilt host with an empty backups volume wipe the remote history. A `crypt` remote is recommended, because the Keycloak dump holds password hashes and the client secret. `.env` and `rclone.conf` are not in the backup and belong in a password manager.
- **Restore.** Rehearsed locally on 2026-09-29: `pg_restore --clean --if-exists` into the empty databases that `init-databases.sh` creates, then untar the images with `--strip-components=1` and `chown 10001`. Table owners are kept. The fresh-machine test (task 9.2) remains an operator task.
- **Cloudflare Tunnel variant.** `docker-compose.tunnel.yml` adds `cloudflared` (token-based, remotely managed tunnel to `http://caddy:80`). It removes Caddy's published ports (`!reset`), swaps in `Caddyfile.tunnel`, and removes Caddy's `APP_HOST` network alias (`networks: !reset {}`).
  - `Caddyfile.tunnel`: `auto_https off`, `http://{$APP_HOST}`, `header_up X-Forwarded-Proto https` for the app and Keycloak, and `trusted_proxies private_ranges`, so the visitor IP from cloudflared reaches Keycloak's brute-force protection.
  - Without the alias, the app's server-side OIDC calls to `https://<APP_HOST>` go out through Cloudflare, because Caddy serves no TLS in this variant. The app therefore also depends on `cloudflared`, and Docker restarts it until the tunnel is up.
  - `COMPOSE_FILE` in `.env` makes plain `docker compose` commands use the variant.
- **Domain for the tunnel.** A named Cloudflare Tunnel needs a domain whose DNS is on Cloudflare, so DuckDNS can't be used on the Pi. The guide points to free options such as an `eu.org` subdomain, and notes that a paid domain would be the stack's only cost.
- **Google IdP in the realm file.** It is still imported disabled. With empty `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, an enabled provider would show a broken Google button in dev, in tests, and in deployments without Google. Enabling it is part of the operator's task 9.9, in the admin console.
