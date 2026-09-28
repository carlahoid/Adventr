## 0. Preconditions

- [ ] 0.1 Confirm that `add-friend-adventures-mvp` is fully implemented and archived (`openspec/specs/user-auth/spec.md` exists), and note the last Flyway version in use
- [ ] 0.2 Confirm where the MVP image processing (task 6.7) lives, and whether it can be called with a size or crop parameter without touching adventure storage

## 1. Design tokens and dark theme (appearance)

- [ ] 1.1 Replace every color literal outside the token blocks in `app.css` with tokens (`--surface`, `--surface-2`, `--border-strong`, `--danger*`, `--notice-*`, `--error-*`, `--disabled-*`, `--accent-text`, `--focus-ring`), keeping the current light look except where contrast requires a change
- [ ] 1.2 Add the dark token values under `:root[data-theme="dark"]` and `@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) }`, plus `color-scheme` for native controls
- [ ] 1.3 Map `--accent`, `--accent-fg`, and `--accent-text` to the per-theme `-l`/`-d` variables, with default values in the stylesheet, so pages render correctly without the inline style
- [ ] 1.4 Add a global `:focus-visible` outline using `--focus-ring`, give text inputs the `--border-strong` boundary, and add non-color-only hover states for buttons and links
- [ ] 1.5 Add `TokenContrastTests`: parse both token blocks of `app.css` and assert the declared foreground/background pairs (4.5:1 for text, 3:1 for UI and focus)

## 2. Contrast-safe accent colors (appearance)

- [ ] 2.1 Implement `AccentColors` in `app.adventr.account`: hex parsing and validation, WCAG luminance and contrast, OKLCH conversion with a gamut clamp, and per-theme derivation of `accent`, `accent-fg`, and `accent-text` against that theme's `--bg` and `--surface`
- [ ] 2.2 Define the presets (about 8 named hues, green `#1f7a5c` as the default) and tune them against the final light and dark tokens
- [ ] 2.3 Tests: 1,000 random colors plus edge cases (`#ffff00`, `#0000ff`, `#000000`, `#ffffff`, and colors equal to the backgrounds) meet all thresholds in both themes; presets need no or only minimal adjustment; the stored input is never changed

## 3. Data model and display name sync (user-auth, account-profile)

- [ ] 3.1 Flyway `V<next>__account_and_appearance.sql`: add `display_name_custom boolean not null default false`, `bio varchar(160)`, `avatar_path varchar(255)`, `theme varchar(6) not null default 'SYSTEM'` with a check constraint, and `accent_color char(7)` with a `^#[0-9a-f]{6}$` check constraint
- [ ] 3.2 Map the new fields on `User`, and add the `Theme` enum
- [ ] 3.3 Change `UserRepository.upsert` to keep `display_name` when `display_name_custom` is set, and to return `id, display_name`; make `UserService.provision` return the effective name
- [ ] 3.4 Add the `GroupAccessService.requireSharedGroup(viewerId, userId)` check (self or current co-member, otherwise `GroupAccessDeniedException` → 404), backed by one `exists` query on active memberships
- [ ] 3.5 Tests: a custom name survives a re-login with a changed Keycloak name, email still syncs, a non-custom name still syncs, the header shows the custom name right after login, and `requireSharedGroup` handles self, co-member, former co-member, and stranger

## 4. Account page and profile (account-profile)

- [ ] 4.1 Create `AccountService` and `AccountController` (`GET /account`, `POST /account/profile`, `POST /account/profile/reset-name`), keyed only on `CurrentUser`
- [ ] 4.2 Build `templates/account/account.html` with Profile, Appearance, and Account sections, following the existing card and form patterns, and a responsive layout at 360 px width
- [ ] 4.3 Add validation: display name 1–60 characters trimmed with no control characters, and bio ≤ 160 characters with line breaks collapsed. Show field errors inline and success as a flash message (PRG)
- [ ] 4.4 Add the reset-to-Keycloak-name action, using `UserService.displayNameOf` on the current `OidcUser`
- [ ] 4.5 Add the Account section link to `{issuer}/account`, derived from the `keycloak` client registration
- [ ] 4.6 Make the header name a link to `/account`, and show the bio in the group member list
- [ ] 4.7 Tests: edits are visible in the header, member list, and author labels; invalid input is rejected; a tampered user id is ignored; bio XSS is escaped; unauthenticated access redirects to Keycloak; POST without CSRF returns 403

## 5. Avatars (account-profile)

- [ ] 5.1 Reuse or extract the image processing from MVP 6.7 so it supports a square center-crop to 256 px, with a 5 MB limit for avatars
- [ ] 5.2 Implement avatar storage under `/data/images/avatars/{userId}/{uuid}.jpg`, recording `avatar_path`, and delete the old file after commit on replace and remove
- [ ] 5.3 Add `POST /account/avatar` (multipart) and `POST /account/avatar/remove`, with error messages for rejected files
- [ ] 5.4 Add `GET /users/{userId}/avatar?v={uuid}` behind `requireSharedGroup`, with `Cache-Control: private, max-age=31536000, immutable`
- [ ] 5.5 Build an `avatar(user, size)` Thymeleaf fragment with an initials fallback (`aria-hidden`, name always shown as text), and use it in the header, member list, and `fragments/author.html`. Extend `Author`, `MemberView`, and `CurrentUser` with the avatar version and a visibility flag as needed
- [ ] 5.6 Tests: upload/replace/remove file lifecycle, rejection of a renamed PDF and of an oversized file, EXIF stripped and 256 × 256 output, 404 for a stranger and a former co-member, 200 for self and co-member, and initials shown when no avatar is set

## 6. Appearance settings (appearance)

- [ ] 6.1 Add `POST /account/appearance` (theme and color, with preset or custom color and a reset to default), validated server-side, PRG with a flash message
- [ ] 6.2 Extend `LayoutModelAdvice` with an `appearance` model attribute (theme plus derived light and dark variants). Use defaults for logged-out pages
- [ ] 6.3 Render `data-theme` (omitted for SYSTEM) and the inline `--accent-*-l`/`-d` variables on `<html>` in `layout.html`, from server-computed values only
- [ ] 6.4 Build the Appearance form: theme radios, preset swatches as named radios with a non-color selected marker, a native `<input type="color">`, and the reset button
- [ ] 6.5 Add `GET /account/appearance/preview` returning the preview fragment (button, link, and focus ring in light and dark, plus the "Adjusted for readability" note), triggered by htmx `input changed delay:150ms`
- [ ] 6.6 Tests: theme and color persist and render on the next page, SYSTEM omits `data-theme`, an invalid color (`red;…`, `#12345`) is rejected, the preview does not save, and the landing page renders defaults

## 7. Contrast and accessibility audit (appearance)

- [ ] 7.1 Create `docs/accessibility.md` with the audit checklist: every screen (landing, my groups, group list, adventure detail and edit, comments, reactions, group settings, join, account, error pages) × light/dark × default color, `#ffff00`, and `#0a0a40`
- [ ] 7.2 Walk through the checklist with the browser accessibility inspector and a free axe-core browser extension. Check text, buttons, links, inputs, cards, navigation and the switcher, flash messages, hover, focus, and disabled states
- [ ] 7.3 Fix every finding. Make sure no state is color-only: own reaction (`aria-pressed` plus weight or outline), status badges, flash messages (text or icon prefix), form errors, and selected swatches
- [ ] 7.4 Do a keyboard-only pass on every screen: the focus is always visible, the order is logical, and the switcher `details` and htmx-swapped fragments keep a sensible focus
- [ ] 7.5 Record the results and any accepted exceptions in `docs/accessibility.md`, and add any new token pairs to `TokenContrastTests`

## 8. Wrap-up

- [ ] 8.1 Run `./mvnw verify`: all tests, including the ArchUnit rules, pass
- [ ] 8.2 Manual smoke test in the dev stack: rename, reset the name, upload and remove an avatar, see it as a co-member and not as a stranger, switch themes, pick a preset and `#ffff00`, log out and in again, and check that everything persists with no flash
- [ ] 8.3 Record small decisions in an "Implementation Notes" section of this change's `design.md`
