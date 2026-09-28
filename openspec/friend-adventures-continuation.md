# Continuation Prompt: Friend Adventure List (Shared Bucket List)

You are continuing a project that was explored in a previous thread (`/openspec-explore`). The exploration is **complete**, and all core decisions are made. Your next job is to turn them into an **OpenSpec change proposal**: a proposal, specs per capability, and a task list. Do not re-open settled decisions unless I ask. Where a small detail is still open, pick a sensible default and state it.

## Product idea

A free website where users create **groups** with their friends and keep a shared **list of adventures** (things they want to do together). Group members react with thumbs up or down and comment on each adventure. Every adventure has its own detail page.

Hard requirements:
- **Completely free** for everyone: free and open-source software only, and free hosting.
- All friends get real user accounts.
- Tech: **Java** and **Keycloak**, plus other free technologies.

## Decided: structure and access

- Any user can **create their own groups**, and a user can belong to **many groups**.
- Groups are **private only**. There is no public search, and the only way in is an **invite link**.
- Invite links are **reusable** (they can be shared in a WhatsApp group), have an **expiry**, and the owner can **regenerate** them, which invalidates the old one.
- Join flow: `/join/{token}` → Keycloak login or registration → added to the group.
- Roles per group:
  - **Owner:** everything, including deleting the group, removing members, and transferring ownership.
  - **Member:** add adventures, react, comment, and edit or delete their own content.
  - An Admin role is deferred until after the MVP.
- If a member leaves or is removed, their adventures and comments **stay** (shown as "former member").
- If the owner leaves, ownership must be transferred first, or the longest-standing member is promoted automatically.
- The golden rule is **one central authorization check**: every group-scoped request verifies that the user is a member of that group, in the service layer rather than scattered across controllers.

## Decided: authentication

- **Keycloak handles identity only:** login, self-registration, password reset, and optional Google login.
- Groups, memberships, roles, and invites live in the **app database**, not in Keycloak groups.
- On first login, the app creates a local `User` row keyed by the Keycloak `sub`.

## Decided: adventures

```
Adventure
  group_id, created_by
  title              (REQUIRED, the only required field)
  description        (optional, longer text)
  location           (optional, free text; no maps in the MVP)
  date_from/date_to  (optional)
  time_hint          (optional free text, e.g. "summer", "some weekend")
  cost_estimate      (optional, amount + note such as "per person")
  link               (optional URL)
  image              (optional, one uploaded image, compressed/resized, stored on disk)
  status             IDEA | PLANNED | DONE
  created_at, updated_at
```

- Adding an idea must take about 5 seconds, which is why only the title is required.
- **Any member** can change the status.
- In the list, PLANNED adventures appear at the top, and DONE adventures go to a "memories" section.

## Decided: reactions

```
Reaction
  adventure_id, user_id, type (UP | DOWN), created_at
  UNIQUE (adventure_id, user_id)
```

- Each user has one reaction per adventure, and clicking toggles it:
  - none → click 👍 → 👍, click 👎 → 👎
  - 👍 → click 👍 → none, click 👎 → 👎
  - 👎 → click 👍 → 👍, click 👎 → none
- Implemented as an upsert or delete, with the database constraint preventing duplicates.
- The list shows **separate counts** (👍 5 · 👎 1) and is **sorted by net score**, with newest first as the tiebreaker.
- **Names of who reacted** are visible (on hover, or on the detail page).

## Decided: comments

```
Comment
  adventure_id, author_id, text, created_at, edited_at (nullable)
```

- A **flat** thread, oldest first, with no nesting.
- Authors can edit and delete their own comments, and the owner can delete any comment. Edited comments show "(edited)".
- Reactions and comments are **independent**: a user can react without commenting and comment without reacting.

## Decided: pages

1. **My groups:** the landing page after login, with a create-group button.
2. **Adventure list** (`/groups/{id}`): each item shows its title, 👍/👎 counts, comment count, and the user's own reaction state.
3. **Adventure detail** (`/groups/{gid}/adventures/{aid}`): all fields, the reaction buttons with the names of who reacted, and the comment thread with an input box.
4. **Group settings:** members, invite link generate/regenerate, rename, leave or delete the group.
5. **Join landing:** `/join/{token}`.
6. A group switcher in the header.

## Decided: stack and hosting

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3, Java 21, Spring Security (OAuth2/OIDC with Keycloak), Spring Data JPA |
| Frontend | Thymeleaf + htmx: server-rendered, with htmx fragment swaps for reactions and comments |
| Database | PostgreSQL, also used by Keycloak through a separate database |
| Auth | Keycloak with one realm and self-registration enabled |
| Deploy | Docker Compose: app, Keycloak, Postgres, and Caddy (automatic HTTPS) |
| Hosting | Oracle Cloud Always Free (ARM VM) as the primary option, with a Raspberry Pi 4/5 (8 GB) plus Cloudflare Tunnel as the alternative |
| Domain | Free subdomain (DuckDNS or a Cloudflare Tunnel hostname) |

Keycloak needs about 512 MB–1 GB of RAM, which is why a heavier free VM is required.

## MVP scope

Keycloak registration and login, create a group, invite by link, join via link, a per-group adventure list, an adventure detail page, 👍/👎 reactions, comments, Owner + Member roles, adventure status, and a single image upload.

**Out of scope for the MVP:** Admin role, maps and geocoding, notifications and email, public groups, threaded comments, multiple images.

## Your task in this thread

Run `/openspec-proposal` (or produce the equivalent OpenSpec artifacts) for this MVP:
1. `proposal.md`: why, what changes, and impact.
2. Specs per capability: **auth/users, groups & memberships, invites, adventures, reactions, comments, deployment**. Each should have requirements with scenarios (WHEN/THEN).
3. `tasks.md`: an ordered implementation checklist, starting with the project skeleton, Docker Compose, and Keycloak realm setup.

Ask me at most one question, and only if something blocks the proposal.
