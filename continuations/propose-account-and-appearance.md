# Continuation Prompt: Propose "Account and Appearance"

You are continuing work on Adventr, a free web app where friend groups keep a private, shared list of adventures. In an earlier thread (`/openspec-explore`), I checked a generic feature prompt (`openspec/prompts.md`) against the real codebase and we settled what is actually new. Your job in this session is to **write a new OpenSpec change proposal**: proposal, design, specs, and tasks. **Do not write application code.** Do not re-open the decisions below unless I ask. Where a small detail is still open, pick a sensible default and state it in `design.md`.

## Where everything is

The repo is `/Users/carla/Documents/Adventr`.

| Path | What it holds |
|---|---|
| `openspec/changes/add-friend-adventures-mvp/` | The active MVP change: `proposal.md`, `design.md` (read its **Implementation Notes** at the end), `specs/<capability>/spec.md` for user-auth, groups, invites, adventures, reactions, comments, and deployment, and `tasks.md` |
| `openspec/prompts.md` | The generic feature prompt. It was written for an unknown codebase, so treat it as **raw input only**. This prompt supersedes it wherever they differ |
| `continuations/` | Earlier continuation prompts. Background only |

**Start by reading** the MVP `design.md` and the `user-auth` spec, then look at `src/main/resources/static/css/app.css`, `templates/layout.html`, `templates/fragments/header.html`, `web/LayoutModelAdvice.java`, `user/UserProvisioningOidcUserService.java`, and `db/migration/V1__users.sql`. Then **run `/opsx:propose`** to create the change.

## Current state (2026-09-28)

- The MVP change has task groups 1–5 done: skeleton, Docker Compose with Keycloak, auth, groups, and invites. Groups 6–9 (adventures, reactions, comments, and deployment) are **not** started.
- Stack: Spring Boot 4.1 on Java 21, Spring Security OAuth2 login against Keycloak, JPA with Flyway on Postgres, and server-rendered Thymeleaf with htmx 2. No JS framework and no CSS framework.
- `app.css` already has tokens in `:root` (`--bg`, `--fg`, `--muted`, `--accent`, `--accent-fg`, `--border`). About a dozen colors are still hardcoded outside `:root`, e.g. `#fff` surfaces (the header, cards), the danger red `#b42318`, and the flash/success backgrounds `#e6f4ee`, `#fdecea`, and `#f1c4bf`.
- `users` has only `keycloak_sub`, `display_name`, `email`, `created_at`, and `deleted_at`. **Provisioning overwrites `display_name` and `email` from Keycloak on every login.**
- `git` is on `main` and clean.

## What we decided in the exploration

### What is NOT part of this change

The generic prompt listed five items. Three of them are already covered:

1. **Adventures** are already task group 6 of the MVP change, with a *different* model: adventures belong to a **group**, not to a user. The creator edits, the owner can delete, and any member changes the status. **Do not change that model**, and do not add "my adventures" features.
2. **Free access** needs no code. There is no payment code, and the whole stack is free and open source. "Free" means *nobody ever has to pay for anything*. It does **not** mean public content: groups stay private, and everything except `/` still requires login. State it in the proposal as a constraint: no feature may require payment.
3. **Google login** needs no app code. The Keycloak realm (`keycloak/realm-adventr.json`) already contains a `google` identity provider with `enabled: false`. The decision is to **turn it on at go-live** (MVP task group 9), because that's when a real hostname exists. It helps the most for friends who join via a WhatsApp invite link, since they won't need a new password. See "Also do" below.

### What IS in this change: `account-and-appearance`

A single **"My account"** page for the logged-in user, with three sections:

```
  ┌─ My account ──────────────────────────────┐
  │  Profile                                  │
  │   [avatar?] Display name  [__________]    │
  │             Bio           [__________]    │
  │                                           │
  │  Appearance                               │
  │   Theme   ( ) Light  ( ) Dark  (•) System │
  │   Color   ● ● ● ● ● ●   [ custom… ]       │
  │           preview: [Button] link  focus   │
  │                                           │
  │  Account                                  │
  │   Email, password, Google → Keycloak      │
  └───────────────────────────────────────────┘
```

**Profile**
- "Profile" means **your own page to edit**. It is **not** a public profile, and other users cannot view it. In a group, users only see who is in the group, through the existing member list.
- There is **no** list of "my adventures" on it.
- **Display name conflict:** Login sync currently overwrites the name. My leaning is an app-side override, e.g. `display_name_override`, where the effective name is the override if set and the Keycloak name otherwise. Keycloak keeps syncing and user edits survive. Every place that shows a name (the header, the member list, `AuthorLabels`, and later adventures and comments) must use the effective name. Pick the concrete design and justify it.
- Email, password, and linked Google account stay in Keycloak. Link to the Keycloak account console instead of rebuilding those screens.
- A bio is optional plain text with a length limit, rendered escaped.

**Appearance**
- **Themes:** Light, Dark, and System (the default). System follows `prefers-color-scheme`.
- **Persistence:** Save the theme on the **user row**, so it follows the user across devices. The logged-out landing page just uses System and the default color. No cookie is needed unless you find a strong reason.
- **No flash:** Because the pages are server-rendered, the layout can render `data-theme` and the accent variables into `<html>` directly, e.g. via `LayoutModelAdvice`. No client-side script is needed for that.
- **Primary color:** Offer **both** a curated palette of about 6–8 presets and a **free custom picker**. Use native `<input type="color">` and no new dependency.
- **Contrast safety for any color:** When a color is saved, the server derives safe variants:
  - `--accent-fg` is black or white, whichever contrasts more. One of them always reaches at least 4.5:1.
  - `--accent-text` is used for links and accent-colored text. It's the picked hue, darkened (light theme) or lightened (dark theme) until it reaches at least 4.5:1 against the background.
  - The focus ring must reach at least 3:1 against the background.
  - The UI keeps the user's hue and stays readable. Show the adjusted result in the preview.
  - The curated presets are checked once in both themes.
- **Security:** Validate the stored color strictly on the server (e.g. `^#[0-9a-f]{6}$`) and never interpolate raw input into `style`. If a Content Security Policy exists or is planned, make sure the approach is compatible with it.

**CSS token refactor and contrast audit**
- First replace every hardcoded color with tokens, and add the missing ones, e.g. `--surface`, `--danger`, `--success-bg`, `--danger-bg`, and `--focus-ring`. Then define dark values for all tokens.
- Audit every screen in light and dark mode, and with the extremes of the custom color: text, backgrounds, buttons, links, inputs, cards, navigation, the group switcher, flash messages, hover, focus, and disabled states.
  - Aim for WCAG AA: 4.5:1 for text and 3:1 for UI components and focus indicators.
  - Keyboard focus must always be visible.
  - Color must never be the only way to convey information.
- The **audit is the last task group** and covers every screen that exists when it runs. This change is meant to be implemented **after MVP groups 6–8**, so the audit includes the adventure, reaction, and comment screens.

### Capabilities for the new change

- `account-profile` (new): the My account page, display name override, bio, and optional avatar, plus the rule that only you can view and edit your own account.
- `appearance` (new): theme, primary color, persistence, no-flash rendering, derived contrast variants, and the contrast and focus requirements.
- `user-auth` (modified): the effective display name and the rule that login sync no longer clobbers user edits. Write this as a delta spec against the MVP's `user-auth` spec.

## Still open: ask me first

Ask me these two before you write the artifacts, in one message:

1. **Avatar or not?** An avatar needs image upload. It could reuse the adventure image pipeline from MVP task 6.7 (magic-byte check, EXIF strip, resize, and a protected endpoint). Leaving it out keeps the change small.
2. **Timing:** Implement after the MVP goes live, or between groups 8 and 9, so friends get dark mode on day one? Either way, the tasks should assume groups 6–8 are done.

## Also do: small edit to the MVP change

- Add a task to **group 9** of `openspec/changes/add-friend-adventures-mvp/tasks.md` to enable Google login at go-live:
  - Create a Google Cloud OAuth client.
  - Set the redirect URI to `https://<APP_HOST>/auth/realms/adventr/broker/google/endpoint`.
  - Put `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env`.
  - Set the IdP to `enabled: true`.
  - Publish the consent screen. Testing mode only allows listed test users. The basic `openid email profile` scopes need no Google review.
  - Smoke test: log in with Google, and check account linking for an email address that already exists.
- In the MVP `design.md` **Open Questions**, mark the Google question as resolved: "enable at first deploy (task 9.x)".

## How to work

1. Only OpenSpec artifacts. **No application code, migrations, or CSS changes in this session.**
2. Follow the style of the MVP change's artifacts: WHEN/THEN scenarios in specs, and small ordered checkbox tasks with tests listed per group. Match its naming conventions (Flyway `V<n>__…`, packages under `app.adventr.*`, e.g. `app.adventr.account`).
3. Keep the design grounded in the existing code: reuse `LayoutModelAdvice`, `CurrentUser`, the fragment layout, and the image pipeline. Don't add dependencies without a clear reason.
4. When done, run `openspec validate` on the new change (or `openspec status`) and summarize what you created, the defaults you picked, and anything that looked wrong.
5. Commit the artifacts with a `docs(openspec): …` message on `main` (or on a branch if I ask). Don't push.
