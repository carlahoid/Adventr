## 1. Project skeleton

- [x] 1.1 Generate a Spring Boot 4 / Java 21 Maven project (`web`, `security`, `oauth2-client`, `data-jpa`, `validation`, `thymeleaf`, `actuator`, `flyway`, `postgresql`) with the base package `app.adventr`
- [x] 1.2 Add the htmx static asset, the Thymeleaf layout dialect (or fragment-based layout), and a base layout with header, group-switcher slot, and CSRF `hx-headers` on `<body>`
- [x] 1.3 Add Testcontainers (Postgres, Keycloak via `dasniko/testcontainers-keycloak`) and ArchUnit test dependencies, and create a context-loads smoke test
- [x] 1.4 Add a multi-stage `Dockerfile` for the app (Temurin 21 JRE, multi-arch base image, non-root user, explicit `-Xmx`)
- [x] 1.5 Add `.gitignore` entries (`.env`, `target/`, `.idea/`, `.DS_Store`) and a README section on local development

## 2. Docker Compose and Keycloak realm

- [x] 2.1 Write `docker-compose.yml` with `postgres` (init script creating the `adventr` and `keycloak` databases and users), `keycloak`, `app`, and `caddy`, plus named volumes `pgdata`, `images`, `caddy_data`, and `backups`
- [x] 2.2 Add health checks and `depends_on: condition: service_healthy` ordering (postgres → keycloak → app)
- [x] 2.3 Configure Keycloak for production behind a proxy (`KC_HTTP_RELATIVE_PATH=/auth`, `KC_HOSTNAME`, `KC_PROXY_HEADERS=xforwarded`, `KC_HTTP_ENABLED=true`, heap limits) with `--import-realm`
- [x] 2.4 Create `keycloak/realm-adventr.json`: the realm `adventr`, registration on, reset-password on, email verification off, a confidential client `adventr-app` with redirect URIs restricted to `${APP_BASE_URL}/*`, SMTP settings from env, and an optional Google IdP disabled by default
- [x] 2.5 Write the `Caddyfile`: `{$APP_HOST}` with `/auth/*` → keycloak and `/*` → app, HTTP → HTTPS, and no static route to images
- [x] 2.6 Add `.env.example` documenting every variable (hostnames, DB passwords, client secret, SMTP, Google credentials, backup remote)
- [x] 2.7 Add `docker-compose.dev.yml` for local development (Keycloak on `localhost:8081`, app run from the IDE) and verify login against a local realm

## 3. Authentication and users (user-auth)

- [x] 3.1 Configure Spring Security `oauth2Login()` against the Keycloak issuer. Require authentication for everything except `/`, static assets, and error pages. Keep CSRF enabled
- [x] 3.2 Flyway `V1`: `users` table (`keycloak_sub` unique, `display_name`, `email`, `created_at`, `deleted_at`)
- [x] 3.3 Implement user provisioning on login (a custom `OidcUserService` or a success handler) that upserts by `sub` and refreshes the name and email, and expose a `CurrentUser` resolver
- [x] 3.4 Implement RP-initiated logout (`OidcClientInitiatedLogoutSuccessHandler`) back to `/`
- [x] 3.5 Verify that the saved-request redirect returns users to the originally requested URL after login
- [x] 3.6 Tests: first login creates a user, a second login updates without duplicating, POST without CSRF returns 403, and unauthenticated access redirects to Keycloak

## 4. Groups, memberships, and central authorization (groups)

- [x] 4.1 Flyway `V2`: `groups` and `memberships` (role, `joined_at`, `left_at`, unique `(group_id, user_id)`)
- [x] 4.2 Implement `GroupAccessService.requireMember` / `requireOwner` and map `GroupAccessDeniedException` to 404 and `ForbiddenActionException` to 403
- [x] 4.3 Add an ArchUnit/convention test ensuring that group-scoped services use the guard and that controllers do not access repositories directly
- [x] 4.4 Build the create-group flow (name 1–80 characters, creator becomes the Owner) and the "My groups" landing page with an empty state
- [x] 4.5 Build the header group switcher (a model attribute listing the user's active groups)
- [x] 4.6 Build the group settings page: member list with roles and join dates, and owner-only rename
- [x] 4.7 Implement leave group (Member leaves, Owner blocked while others remain, sole Owner leaving deletes the group) and remove member (Owner only)
- [x] 4.8 Implement ownership transfer and delete group (confirmation by typing the name, cascade including image files)
- [x] 4.9 Implement the automatic owner promotion service for account deletion (longest-standing member, or delete the group)
- [x] 4.10 Add the "former member" label helper used by all author and reactor displays
- [x] 4.11 Tests: non-member gets 404, former member gets 404, Member→owner action gets 403, and the leave/transfer/delete rules

## 5. Invites (invites)

- [x] 5.1 Flyway `V3`: `invites` (unique `group_id`, unique `token`, `expires_at`, `created_by`)
- [x] 5.2 Implement generate/regenerate (32-byte `SecureRandom`, base64url, 7-day expiry, replace the existing row), owner only
- [x] 5.3 Show the invite link with a copy button and expiry on the settings page for all members
- [x] 5.4 Build `/join/{token}`: authentication required, then the join landing page (group name, "Join" button) with valid, expired, invalid, already-member, and rejoin handling
- [x] 5.5 Tests: reuse by several users, old token invalid after regenerate, expired token, and a new-user registration round trip (Testcontainers Keycloak)

## 6. Adventures (adventures)

- [ ] 6.1 Flyway `V4`: `adventures` with all fields, `status`, `status_changed_at`, and indexes on `(group_id, status)`
- [ ] 6.2 Implement `AdventureService` (create, update, delete, change status) with the guard, group-scoped lookups, and creator/owner permission rules
- [ ] 6.3 Add validation: title 1–120 characters, field lengths, `date_to ≥ date_from`, `link` restricted to http/https, `cost_amount ≥ 0`
- [ ] 6.4 Write the list query: one aggregate query returning up/down counts, net score, comment count, and the current user's reaction, plus the section split and ordering
- [ ] 6.5 Build the adventure list page (Planned / Ideas / Memories sections, empty state) with the htmx quick-add form (title only)
- [ ] 6.6 Build the adventure detail page (set fields only, creator, status control, edit/delete for permitted users) and the full edit form
- [ ] 6.7 Implement the image upload pipeline: 10 MB limit, magic-byte check (JPEG/PNG/WebP), EXIF orientation, metadata stripping, resize to 1600 px max, JPEG re-encode, storage under `/data/images/{groupId}/{uuid}.jpg`, and old-file deletion on replace/remove/delete
- [ ] 6.8 Add the protected image endpoint `GET /groups/{gid}/adventures/{aid}/image` behind the guard with `Cache-Control: private`, plus list thumbnails
- [ ] 6.9 Tests: ordering scenarios, status change by a non-creator, a non-creator edit returning 403, XSS escaping, image rejection cases, and a non-member image request returning 404

## 7. Reactions (reactions)

- [ ] 7.1 Flyway `V5`: `reactions` with primary key `(adventure_id, user_id)`
- [ ] 7.2 Implement `ReactionService.toggle` (insert/update/delete per the toggle table, retrying once on a constraint violation)
- [ ] 7.3 Build the reaction-bar Thymeleaf fragment (counts, own-state highlight, reactor names as a tooltip) and an htmx POST endpoint returning the fragment, with a non-JS redirect fallback
- [ ] 7.4 Show the reactor name lists (👍/👎) on the detail page, including the "former member" label
- [ ] 7.5 Tests: all six toggle transitions, concurrent double-click uniqueness, and net-score ordering in the list

## 8. Comments (comments)

- [ ] 8.1 Flyway `V6`: `comments` (`adventure_id`, `author_id`, `text`, `created_at`, `edited_at`)
- [ ] 8.2 Implement `CommentService` (post, edit by author only, delete by author or owner) with the guard and validation (1–2000 characters)
- [ ] 8.3 Build the comment thread fragment (oldest first, line breaks preserved, "(edited)" marker, "former member" label) with htmx post/edit/delete swaps
- [ ] 8.4 Show the comment count in the list
- [ ] 8.5 Tests: ordering, an owner edit returning 403, owner delete allowed, and a member deleting another's comment returning 403

## 9. Deployment, backups, and go-live (deployment)

- [ ] 9.1 Add a `backup` service or host cron script: nightly `pg_dump` of both databases plus a tar of the images volume, rotation (7 daily, 4 weekly), and an `rclone` copy to a free off-host remote
- [ ] 9.2 Write `docs/restore.md` and perform one test restore on a fresh machine
- [ ] 9.3 Add `docker-compose.tunnel.yml` (the Pi variant): a `cloudflared` service, with Caddy running without public ports or removed
- [ ] 9.4 Write `docs/deploy-oracle.md`: account and PAYG upgrade to avoid idle reclamation, ARM VM provisioning, firewall/security list for 80/443, Docker install, DuckDNS updater, `.env`, and `compose up`
- [ ] 9.5 Write `docs/deploy-pi.md`: OS, Docker, Cloudflare Tunnel setup, and `compose -f ... -f docker-compose.tunnel.yml up`
- [ ] 9.6 Configure the SMTP relay in `.env` and verify the password-reset email end to end
- [ ] 9.7 Run the production smoke test: register, create a group, invite a second account, join, add an adventure with an image, react, comment, and log out, then check that only 80/443 are exposed
- [ ] 9.8 Check the 24-hour memory stability of the stack on the target host and tune the JVM heap limits if needed
- [ ] 9.9 Enable Google login: create a Google Cloud OAuth client with the redirect URI `https://<APP_HOST>/auth/realms/adventr/broker/google/endpoint`, put `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` in `.env`, set the `google` IdP to enabled (realm file and admin console, since the realm import runs only once), publish the consent screen (basic `openid email profile` scopes need no Google review), document the steps in `docs/deploy-oracle.md`, and smoke test a Google login plus account linking for an email that already has a password account
