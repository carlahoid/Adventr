# Continuation Prompt: Implement the Friend Adventures MVP, Part 2 (Groups and Invites)

You are continuing the **implementation** of Adventr, a free web app where friend groups keep a private, shared list of adventures. The OpenSpec change is the single source of truth. **Do not re-open decisions made there.** Where a small detail is missing, pick a sensible default, follow the style of the design, and record it in `design.md` under "Implementation Notes".

## Where everything is

The repo is `/Users/carla/Documents/Adventr`. The change is `openspec/changes/add-friend-adventures-mvp/`:

| File | What it holds |
|---|---|
| `proposal.md` | Why the app exists, what's in scope, and the 7 capabilities |
| `design.md` | Technical decisions, plus an **"Implementation Notes" section at the end. Read it first**, because it records everything decided during groups 1–3 |
| `specs/<capability>/spec.md` | Requirements with WHEN/THEN scenarios. This session needs `groups` and `invites` |
| `tasks.md` | 61 ordered checkbox tasks in 9 groups. Groups 1–3 are ticked |

`continuations/implement-friend-adventures-mvp.md` is the part 1 prompt. It is background only.

**Start by running `/opsx:apply add-friend-adventures-mvp`**, then read `design.md` (including the Implementation Notes) and the `groups` and `invites` specs before writing any code.

## Current state (end of part 1, 2026-09-28)

- **Done:** Task groups 1–3 (18/61 tasks): project skeleton, Docker Compose with the Keycloak realm, and authentication. `./mvnw verify` passes 16 tests.
- **Git:** Branch `feat/mvp-skeleton-auth` has one commit per group plus a docs commit. It is not pushed and not merged into `main`. Before branching, check with `git branch` / `git log` whether I merged it.
- **Stack decisions made in part 1:**
  - **Spring Boot 4.1.1**, not 3. I decided this because 3.x is out of OSS support. It uses Spring Security 7 and Testcontainers 2.
  - The code targets Java 21, and the local JDK is 26.
  - **htmx is 2.0.11**, not 4.x, because htmx 4 breaks `hx-headers` inheritance from `<body>`.
- **Local environment:**
  - macOS arm64 with Docker. Testcontainers needs Docker Desktop running; start it with `open -a Docker` if needed.
  - The dev stack may still be running: `docker compose -f docker-compose.dev.yml up -d` (Postgres :5432, Keycloak :8081/auth, Mailpit :8025).
  - Run the app with `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.

## Code conventions already in place (follow them)

- **Package layout:**
  - `app.adventr.config`: `SecurityConfig`, `WebConfig`
  - `app.adventr.user`: `User`, `UserRepository`, `UserService`, `UserProvisioningOidcUserService`, `CurrentUser`, `CurrentUserArgumentResolver`
  - `app.adventr.web`: `HomeController`, `LayoutModelAdvice`
  - Add new capabilities as sibling packages, e.g. `app.adventr.group` and `app.adventr.invite`.
- **Style:** Tabs for indentation, `this.` on field access, constructor injection, and `@Configuration(proxyBeanMethods = false)`. Comments are short Javadoc that explains *why*.
- **Current user:** Declare `CurrentUser currentUser` (a record with `id` and `displayName`) as a controller method parameter. It is resolved from the OIDC principal by `sub`.
- **Placeholders to replace:**
  - `HomeController.myGroups()` (`GET /groups`) is a placeholder. The real "My groups" page is task 4.4, so move or replace it.
  - `LayoutModelAdvice` adds `currentUser` to every model. Task 4.5 adds `switcherGroups` there. The header fragment (`templates/fragments/header.html`) already has an empty `<nav class="group-switcher" th:if="${switcherGroups != null}">` slot.
- **Templates:**
  - Pages use `<html th:replace="~{layout :: page(~{::title}, ~{::main})}">`.
  - `layout.html` puts the CSRF `hx-headers` on `<body>`. `th:action` forms get the CSRF hidden field automatically.
  - `templates/error.html` already renders friendly 404 and 403 pages. Map `GroupAccessDeniedException` to 404 and `ForbiddenActionException` to 403, for example with `@ResponseStatus` or a `@ControllerAdvice`.
- **Database:**
  - Flyway `V1__users.sql` exists. Next come `V2__groups_memberships.sql` and `V3__invites.sql`.
  - IDs are `bigint generated always as identity`, timestamps are `timestamptz`, and `ddl-auto: validate`.
  - Use native `on conflict` upserts where races matter.
- **Tests:**
  - Annotate integration tests with `@IntegrationTest` (`src/test/java/app/adventr/IntegrationTest.java`). It gives a full app on a fixed free port with `@AutoConfigureMockMvc`, a Postgres container, and a Keycloak container that imports the **production** `keycloak/realm-adventr.json`. All tests share one cached context, so don't add `@MockitoBean` or other context-splitting config without a reason.
  - For MockMvc as a logged-in user, use `oidcLogin().clientRegistration(clientRegistrations.findByRegistrationId("keycloak"))`. The `sub` defaults to `"user"`; set `.idToken(t -> t.subject("..."))` to simulate different users.
  - For real end-to-end flows through Keycloak pages (e.g. task 5.5's new-user registration via an invite link), use `app.adventr.Browser`. See `LoginFlowIntegrationTests` for the `register(...)` and `logout(...)` helpers. Keycloak sets `Secure` cookies even on http://localhost, which is why the tests use `Browser` and not `java.net.CookieManager`.
  - Use `TestcontainersConfiguration.APP_BASE_URL` for the app URL. The Keycloak admin client is available via `keycloak.getKeycloakAdminClient()`.
- **Keycloak realm:** It is imported only on first start. If you change `realm-adventr.json`, reset the dev stack with `docker compose -f docker-compose.dev.yml down -v`.

## Your scope for this session: task groups 4 and 5

- **Group 4 (groups and memberships):** Tasks 4.1–4.11. Key points:
  - `GroupAccessService.requireMember` / `requireOwner` is the **one** central authorization check, in the service layer.
  - Non-members and former members get **404**. Members attempting owner actions get **403**.
  - Child entities are always loaded with a `groupId` predicate.
  - Task 4.3's ArchUnit test enforces this: group-scoped services call the guard, and controllers never touch repositories. The ArchUnit dependency is already in the POM.
  - Adventures and images don't exist yet. For "delete group" (4.8) and owner promotion (4.9), cascade what exists now, and leave a clear hook, e.g. a `GroupDeletionListener` or a TODO referencing tasks 6.x, for adventure and image cleanup later.
- **Group 5 (invites):** Tasks 5.1–5.5.
  - Tokens are 32 bytes from `SecureRandom`, base64url-encoded, with a 7-day expiry and one row per group. Regenerating replaces the row.
  - `/join/{token}` requires login. Unauthenticated visitors are sent through Keycloak and back via the saved request, which already works.
  - Handle these cases: valid, expired, invalid/regenerated, already a member, and rejoin (reactivate the membership with a new `joined_at`).
- **Stop after group 5** so I can check it.
  - The finish line: two accounts in the dev setup. Account A creates a group and copies the invite link. Account B opens the link, registers, and lands in the group. Both see the group in "My groups" and in the switcher. A can remove B, after which B gets 404 on the group.
  - Tell me exactly how to try this manually, e.g. with a second browser profile or a private window.

## How to work

1. **Go task by task through `tasks.md`, in order.** Tick each box (`- [x]`) as soon as that task is done and verified.
2. **Tests go with each group**, as listed in the tasks. `./mvnw verify` must pass before you tick a group's last box.
3. If something in the implementation contradicts a spec, **stop and tell me**, and suggest an update to the spec. Do not quietly deviate. Record small additions under "Implementation Notes" in `design.md`.
4. **Commits:**
   - If `feat/mvp-skeleton-auth` is not yet merged, create a new branch **from it**, e.g. `feat/mvp-groups-invites`. Otherwise, branch from `main`.
   - Make one commit per completed task group, using a conventional commit message, plus a separate `docs(openspec): …` commit for task ticks and notes.
   - Don't push unless I ask.
5. **Secrets:** Never commit `.env`. Dev credentials are throwaway values in `docker-compose.dev.yml` and `application-dev.yml`.
6. Ask me at most one question, and only if something truly blocks the work.

## Out of scope (do not build)

The Admin role, maps or geocoding, app notifications or email (Keycloak's password-reset email is the only exception), public groups, threaded comments, and multiple images per adventure. Also, don't start groups 6+ (adventures, reactions, comments, deployment docs) in this session.
