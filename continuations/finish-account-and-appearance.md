# Continuation Prompt: Finish account-and-appearance (Accessibility Audit and Smoke Test)

You are finishing the **account-and-appearance** change of Adventr, a free web app where friend groups keep a private, shared list of adventures.

- **All code for the change is done.** What remains is to check it in a real browser: an accessibility walkthrough, a keyboard pass, and a smoke test in the dev stack. Then fix what they find, and archive the change.
- **The OpenSpec change is the single source of truth.** Do not re-open decisions made there. Where a small detail is missing, pick a sensible default and record it in `design.md` under "Implementation Notes".

## Where everything is

The repo is `/Users/carla/Documents/Adventr`.

| Path | What it holds |
|---|---|
| `openspec/changes/account-and-appearance/` | This change. Read `design.md` first, **including the "Implementation Notes" at the end**: they record every decision made during implementation. `tasks.md` has 42 tasks, 37 of them ticked. The specs are in `specs/{account-profile,appearance,user-auth}/spec.md`. |
| `docs/accessibility.md` | **The audit document.** It holds the checklist of 11 screens × light/dark × three colors, the keyboard pass, an empty results table, the fixes already made in code review, and "Accepted exceptions". |
| `openspec/specs/` | The main specs. The MVP change is archived in `openspec/changes/archive/2026-09-29-add-friend-adventures-mvp/`. |
| `src/test/java/app/adventr/account/TokenContrastTests.java` | The automated contrast guard. Add any new token pairs here (task 7.5). |
| `README.md` | The "Local development" section explains the dev stack. |

**Start by running `/opsx:apply account-and-appearance`**, then read `design.md` (with its Implementation Notes), `docs/accessibility.md`, and the `appearance` spec before doing anything else.

## Current state (2026-09-29)

- **Done:**
  - account-and-appearance tasks 0.1–6.6, 7.1, 8.1, and 8.3.
  - The MVP (adventures, reactions, comments, deployment) was implemented and archived first.
  - `./mvnw verify` passes **161 tests**, including the ArchUnit rules.
- **Open:**
  - **7.2:** walk through the checklist with the browser's accessibility inspector and axe-core.
  - **7.3:** fix every finding.
  - **7.4:** a keyboard-only pass.
  - **7.5:** record the results and exceptions in `docs/accessibility.md`, and add any new token pairs to `TokenContrastTests`.
  - **8.2:** a manual smoke test in the dev stack.
- **Git:** All work is committed and pushed. `main` = `origin/main` at `ab007bd`. The branch `adventures-and-account` is merged and can be ignored.
- **Local environment:**
  - macOS arm64. Docker Desktop must be running for the tests and the dev stack; start it with `open -a Docker` if needed.
  - The local JDK is 26 and the code targets Java 21.

## What the change built (so you know what to test)

- **Design tokens** (`src/main/resources/static/css/app.css`):
  - The light theme is on `:root`. The dark theme is the same block twice: `:root[data-theme="dark"]` and a `prefers-color-scheme: dark` media query on `:root:not([data-theme="light"])`.
  - No color literals appear after `/* End of token blocks. */`.
  - A global `:focus-visible` ring uses `--focus-ring`, which equals `--accent-text`.
  - Inputs and secondary buttons use `--border-strong`.
- **Theme and color rendering:**
  - The server renders them on `<html>`: `data-theme` (omitted for System) plus an inline style with `--accent-l`, `--accent-fg-l`, `--accent-text-l`, and the `-d` variants.
  - `AccentColors` computes these values. They're contrast-safe variants of any picked color, found by stepping OKLCH lightness.
  - Logged-out pages use the defaults.
- **My account (`/account`):**
  - Display name: custom names survive login, with a "Use my Keycloak name" reset. Also a bio.
  - Avatar upload: a 256 px square, served at `/users/{id}/avatar` to the owner and current co-members only, with initials otherwise.
  - Appearance: theme radios, 8 preset swatches plus a custom `<input type="color">`, and a live htmx preview with an "Adjusted for readability" note per theme.
  - A link to Keycloak's account console.
- **htmx focus handling:**
  - **Reaction buttons:** stable ids.
  - **Quick add and the comment edit form:** `autofocus`.
  - **After deleting a comment:** focus moves to the "Comments (n)" heading.
  - **After saving or cancelling a comment edit:** focus moves to its "Edit" link.
- **Known, deliberate behavior (don't "fix" it):**
  - Every color, presets included, gets a lighter link/text shade in dark mode. The math makes it unavoidable: see the Implementation Notes.
  - The default green therefore shows "Adjusted for readability" in the dark preview panel.
  - The header's account link has no `aria-current`, because Thymeleaf 3.1 has no request object in expressions.

## How to run the app and get test data

1. **Start the dev stack.** Keycloak is healthy after about 30 s:
   ```sh
   docker compose -f docker-compose.dev.yml up -d
   ```
2. **Start the app:** `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`. It runs on http://localhost:8080; Flyway migrates to V7 on start.
3. **Other URLs:**
   - Keycloak admin console: http://localhost:8081/auth/admin (`admin` / `admin`)
   - Mailpit: http://localhost:8025
   - Uploaded images go to `target/dev-images`.
4. **Test data:**
   - You need two accounts, e.g. "Kim Climber" and "Ben". Register them through the Keycloak login page, or create them in the admin console (realm `adventr`).
   - With account A, create a group and copy its invite link. Join with account B (second browser profile or private window).
   - Then create at least one Planned, one Idea, and one Done adventure, one of them with an image.
   - Add reactions from both accounts, a comment from each, one edited comment, and an avatar for one account. Let a third account join and leave, to see the "(former member)" label.

## Your scope for this session

1. **Task 7.2, the walkthrough:** go through every row of the checklist in `docs/accessibility.md`: 11 screens × light/dark × default green, `#ffff00`, and `#0a0a40`.
   - Set the theme and color on the account page.
   - For System, also try the OS setting.
   - **Tools:**
     - The browser's accessibility inspector and axe-core.
     - If a Claude-in-Chrome extension is available, load the `claude-in-chrome` skill and drive the browser yourself.
     - Run axe either through an installed axe extension or by injecting `axe-core` from `cdnjs.cloudflare.com` into the page and calling `axe.run()`. That uses it as a tool, not a project dependency.
     - If no browser automation is available, ask me to do the walkthrough and send you the findings.
2. **Task 7.3:** fix every finding in tokens, templates, or CSS.
   - Keep colors in the token blocks only.
   - Every new foreground/background combination gets a pair in `TokenContrastTests`.
   - Make sure no state is conveyed by color alone.
3. **Task 7.4, the keyboard pass:** on every screen, check that focus is always visible, the order is logical, the group switcher `<details>` works with the keyboard, and htmx swaps leave focus where the checklist says.
4. **Task 7.5:** fill in the results table in `docs/accessibility.md`, describe each finding and its fix, and list any accepted exceptions with the reason.
5. **Task 8.2, the smoke test in the dev stack:**
   - Rename, then reset the name.
   - Upload and remove an avatar. Check it as a co-member (visible) and as a stranger (404, initials).
   - Switch between the three themes. Pick a preset, then `#ffff00`.
   - Log out and back in, and check that everything persisted and that no page flashes the wrong theme.
6. **Run `./mvnw verify`** after any fix. It must stay green.
7. **Tick each task** in `tasks.md` as soon as it is done and verified. Add small decisions to the Implementation Notes.
8. **When all 42 tasks are ticked,** tell me and suggest `/opsx:archive account-and-appearance`. Its `user-auth` delta modifies the existing main spec.

## How to work

- **If a finding contradicts a spec or a design decision,** stop and tell me, and suggest an artifact update. Don't quietly deviate.
- **Commits:**
  - Branch from `main`, e.g. `audit/account-and-appearance`.
  - Commit fixes with conventional commit messages (e.g. `fix(a11y): …`), plus a separate `docs: …` commit for the audit results and task ticks.
  - End every commit message with the co-author line your harness specifies.
  - Don't push unless I ask.
- **Secrets:** Never commit `.env`. The dev credentials in `docker-compose.dev.yml` and `application-dev.yml` are throwaway values.
- **Questions:** ask at most one, and only if something truly blocks the work.
- **Stopping the dev stack:** when you're done, `docker compose -f docker-compose.dev.yml down`. Keep the volumes unless I say otherwise.

## Out of scope

- **The MVP's go-live tasks 9.2 and 9.6–9.9:** a restore test on a fresh machine, SMTP, the production smoke test, a 24-hour memory check, and Google login. They run on my real host; the steps are in `docs/deploy-oracle.md`, `docs/deploy-pi.md`, and `docs/restore.md`.
- **Anything the change's non-goals exclude:** public profiles, per-group themes, custom fonts, and a CSP.
- **New features.** Only fix what the audit and the smoke test find.
