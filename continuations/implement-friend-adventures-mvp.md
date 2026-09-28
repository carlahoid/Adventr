# Continuation Prompt: Implement the Friend Adventures MVP

You are starting the **implementation** of Adventr, a free web app where friend groups keep a private, shared list of adventures. In earlier threads, we explored the idea and wrote a complete OpenSpec change. That spec is the single source of truth. **Do not re-open decisions made there.** Where a small detail is missing, pick a sensible default, follow the style of the design, and mention it in your summary.

## Where everything is

The repo is `/Users/carla/Documents/Adventr`, and there is no application code yet. The change is `openspec/changes/add-friend-adventures-mvp/`:

| File | What it holds |
|---|---|
| `proposal.md` | Why the app exists, what's in scope, and the 7 capabilities |
| `design.md` | Technical decisions: architecture, data model, authorization, sorting, images, deployment topology, and risks |
| `specs/<capability>/spec.md` | Requirements with WHEN/THEN scenarios for user-auth, groups, invites, adventures, reactions, comments, and deployment |
| `tasks.md` | 66 ordered checkbox tasks in 9 groups. This is your work queue |

The older exploration notes are in `openspec/friend-adventures-continuation.md`. They're background only; the change files above take precedence.

**Start by running `/opsx:apply`** (or `openspec status --change add-friend-adventures-mvp`), then read `design.md` and the specs before writing any code.

## Core decisions (summary; read `design.md` for the details)

- **Stack:** Spring Boot 3 on Java 21 with Maven, Spring Security OAuth2 login against Keycloak, Spring Data JPA, Flyway, PostgreSQL, and Thymeleaf with htmx. The UI is server-rendered, with htmx fragment swaps for reactions, comments, and quick-add.
- **Keycloak handles identity only.** Groups, memberships, roles, and invites live in the app database. A local `User` row is created or updated on each login, keyed by the `sub` claim.
- **One central authorization point:** `GroupAccessService.requireMember` / `requireOwner` in the service layer.
  - Child entities are always looked up together with their `groupId`.
  - Non-members get 404. Members attempting an owner-only action get 403.
  - Controllers never check membership themselves.
- **Images** are served only through `/groups/{gid}/adventures/{aid}/image`, which goes through the membership check. They are never exposed as static files.
- **Deployment** uses Docker Compose with four services: app, Keycloak (at the path `/auth`), Postgres (separate `adventr` and `keycloak` databases), and Caddy. The Keycloak realm is imported from `keycloak/realm-adventr.json`. Every image must be available for arm64.
- **Everything must be free and open source.**

## Local environment (checked 2026-09-28)

- macOS on Apple Silicon (arm64). Docker 29.8 and Compose v5.5 are installed.
- The installed JDK is **26.0.2**, not 21. Set the project to Java 21 with `<maven.compiler.release>21</maven.compiler.release>` / `java.version=21`; the JDK 26 compiler can build that. Use a Java 21 JRE base image in the Dockerfile. If a tool doesn't work under JDK 26, tell me and don't work around it silently.
- Maven 3.9.14 is installed. Add the Maven wrapper (`mvnw`) to the project.
- Git branch: `main`, which is uncommitted apart from the OpenSpec files. IntelliJ files live in `.idea/`.

## How to work

1. **Go group by group through `tasks.md`, in order.** Tick each box (`- [x]`) as soon as that task is done and verified.
2. **Scope for this first session: task groups 1 to 3** (skeleton, Docker Compose with the Keycloak realm, and authentication). Stop after group 3 so I can check it.
   - The finish line: I can run the dev setup, register a user in Keycloak, log in, get a `users` row in the database, and log out.
   - Tell me exactly how to run it.
3. **Tests go with each group**, as listed in the tasks: Testcontainers for Postgres and Keycloak, and ArchUnit for the authorization convention. `./mvnw verify` must pass before you tick a group's last box.
4. If something in the implementation contradicts a spec, **stop and tell me**, and suggest an update to the spec. Do not quietly deviate. Small additions go into `design.md` under a short "Implementation notes" section.
5. **Commits:**
   - Branch off `main` first (e.g. `feat/mvp-skeleton-auth`).
   - Make one commit per completed task group, using a conventional commit message.
   - Commit the OpenSpec change files in a separate first commit if they aren't committed yet.
   - Don't push unless I ask.
6. **Secrets:** never commit `.env`. Put placeholders in `.env.example`. For local development, generated throwaway secrets are fine.

## Out of scope (do not build)

The Admin role, maps or geocoding, app notifications or email (Keycloak's password-reset email is the only exception), public groups, threaded comments, and multiple images per adventure.

## Open questions (don't block on these)

- Google login is off by default. Keep the Google identity provider in the realm file but disabled.
- The off-host backup target isn't decided yet. That's task 9, so ignore it for now.
