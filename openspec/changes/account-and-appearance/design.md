## Context

Adventr is a server-rendered Spring Boot 4.1 / Thymeleaf + htmx 2 app. Keycloak handles identity, and the app owns everything else (see `add-friend-adventures-mvp/design.md`). This change is implemented **after the MVP is live and archived**, so the adventure, reaction, and comment screens exist and the `user-auth` spec lives in `openspec/specs/`.

Relevant current state:

- **`users` table:** `keycloak_sub`, `display_name`, `email`, `created_at`, `deleted_at`. `UserRepository.upsert` runs on every login and does `on conflict … do update set display_name = excluded.display_name, email = excluded.email`, so any in-app name edit would be lost at the next login.
- **Readers of `display_name`:** the header (`CurrentUser.displayName`), the member list (`MembershipRepository` → `MemberRow`), and `AuthorLabels` → `Author`, which is used for adventure creators, commenters, and reactors.
- **Image pipeline (MVP 6.7):** magic-byte check (JPEG/PNG/WebP), 10 MB limit, EXIF orientation, metadata stripping, resize, JPEG re-encode, storage under `/data/images/…`, and serving only through guarded endpoints with `Cache-Control: private`.
- **`app.css`:**
  - About 270 lines, with six tokens in `:root` (`--bg --fg --muted --accent --accent-fg --border`).
  - About a dozen hardcoded colors: `#fff` surfaces, the danger red `#b42318`, and the flash backgrounds and text.
  - No `:focus-visible` styles, no hover states, and no dark theme.
  - Known contrast gap: `--border` `#e3ded6` is the only visible boundary of text inputs, at about 1.3:1 against white, well under the 3:1 non-text minimum.
- **`LayoutModelAdvice`** already resolves the current user for every authenticated page. `layout.html` is a single fragment layout.
- **No Content Security Policy** is configured, neither in Spring Security nor in Caddy.

Constraints: free and open source only, no new dependencies unless clearly justified, and it must follow the MVP's conventions (package per capability, service-layer authorization, Flyway, htmx fragments, and tests per task group).

## Goals / Non-Goals

**Goals:**
- One self-only "My account" page with display name, bio, avatar, theme, and primary color.
- Custom display names survive Keycloak login sync, and a reset returns to the Keycloak name.
- Avatars are visible only to the user and their current co-members, never through a public URL.
- Light, dark, and system themes plus any accent color, rendered with no theme flash, and readable in every combination.
- A complete design-token system and a documented contrast and focus audit of every screen.

**Non-Goals:**
- Public profile pages, a user directory, or showing a user's adventures on a profile.
- Editing email, password, or linked identity providers in the app. Keycloak's account console already does this.
- Per-group or per-device themes, custom fonts, or anything beyond a single accent color.
- Theme persistence for logged-out visitors. The landing page uses System and the default color.
- Enabling Google login. That is an MVP go-live task and needs no app code.

## Decisions

### 1. Display name: `display_name` stays the effective name, plus a `display_name_custom` flag

`display_name` keeps meaning "the name to show". A new `display_name_custom boolean not null default false` column records whether the user set it themselves. The login upsert becomes:

```sql
do update set
  display_name = case when users.display_name_custom then users.display_name
                      else excluded.display_name end,
  email = excluded.email
returning id, display_name
```

- Saving a name on the account page sets `display_name` and `display_name_custom = true`.
- "Use my Keycloak name" sets `display_name_custom = false` and writes the name derived from the current `OidcUser` principal, using the existing `UserService.displayNameOf`.
- *Why:* All existing readers (header, member list, `AuthorLabels`, and the MVP's adventure/comment views) keep reading one column unchanged, so there is no chance of a place forgetting a fallback.
- *Alternative:* A nullable `display_name_override` column, with `coalesce(override, display_name)` in every reader. Rejected: it touches every query and view, and every future reader has to remember the rule.
- *Alternative:* Make Keycloak the only source and send users to the Keycloak account console to rename. Rejected: it's off-brand and adds a Keycloak round trip for the most common edit.
- `provision()` returns the effective name (from `returning display_name`), not the token name, so the header is right immediately after login.
- Validation: the name is trimmed, 1–60 characters, and must not contain control characters.

### 2. Bio: short plain text, shown only next to you in the member list

A nullable `bio varchar(160)` column holds plain text, rendered escaped, and single-line. Line breaks are collapsed to spaces on save. Your co-members see it in the group member list. It isn't shown anywhere else, because there is no public profile page.

### 3. The account page is keyed on the session, never on a URL id

All account routes act on `CurrentUser` only: `GET /account`, `POST /account/profile`, `POST /account/profile/reset-name`, `POST /account/avatar`, `POST /account/avatar/remove`, `POST /account/appearance`, and `GET /account/appearance/preview`. No route takes a user id, so "editing someone else's profile" cannot even be expressed. This makes the self-only rule structural rather than a check that could be forgotten. It lives in a new package `app.adventr.account` (`AccountController`, `AccountService`). It is not a group-scoped service, so the ArchUnit guard rule doesn't apply to it. The "controllers don't touch repositories" rule still does.

The Account section links to the Keycloak account console, at `{issuer}/account` derived from the `keycloak` client registration's issuer URI. There users change their email, password, and linked Google login.

### 4. Avatars: reuse the image pipeline, square 256 px, and co-member-only serving

- **Processing:** The MVP's image processing is extracted, or reused if it is already separate from adventure storage, as one component with a size parameter. Avatars are center-cropped to a square and resized to 256 × 256 px, then run through the same magic-byte check, EXIF, strip, and re-encode steps, with a 5 MB upload limit.
- **Storage:** `/data/images/avatars/{userId}/{uuid}.jpg`, recorded in `users.avatar_path`. Replacing or removing an avatar deletes the old file after commit, the same way as adventure images.
- **Serving:** `GET /users/{userId}/avatar?v={uuid}`. Access is allowed when the viewer is that user, or when the viewer and that user **currently** share an active membership in some group. Otherwise the response is 404, the same as any group-access denial, so the endpoint can't be used to probe which ids exist.
  - The check lives next to the membership guard: `GroupAccessService.requireSharedGroup(viewerId, userId)`, backed by one `exists` query on `memberships`.
  - The response uses `Cache-Control: private, max-age=31536000, immutable`. This is safe because the `v` parameter changes whenever the avatar changes.
- **Fallback:** Without an avatar, or when the viewer may not see it, the UI shows an initials badge. It displays the first letters of the display name in `--fg` on `--surface-2` and is `aria-hidden`, because the name is always shown next to it. The "former member" case falls under "may not see it": after leaving, you no longer share the group, so old comments show initials.
- *Why co-member only:* Adventr has no public surface, so an avatar is exactly as private as the member list that shows it.
- *Alternative:* Public avatar URLs, e.g. static files served by Caddy. Rejected because it breaks the "no public surface" principle, and it would be the only static route to the images volume.

### 5. Appearance storage: two columns on `users`

- `theme varchar(6) not null default 'SYSTEM'`, with a check constraint for `LIGHT | DARK | SYSTEM`.
- `accent_color char(7)`: nullable, where null means the default green `#1f7a5c`. The value is validated as `^#[0-9a-f]{6}$` after lowercasing, in the form binding and again in a database check constraint.
- Presets are just known hex values. There is no separate "preset id", so a preset and the identical custom color behave the same.
- *Alternative:* A cookie or `localStorage`. Rejected: preferences would not follow the user across devices, and it would need client script to avoid a flash.

### 6. Flash-free theming, rendered on the server

`LayoutModelAdvice` adds an `appearance` model attribute (theme plus derived colors). `layout.html` renders it on `<html>`:

```html
<html lang="en" data-theme="dark"
      style="--accent-l:#1f7a5c;--accent-fg-l:#fff;--accent-text-l:#1f7a5c;
             --accent-d:#3fb68b;--accent-fg-d:#000;--accent-text-d:#5cc79f">
```

- `data-theme` is omitted for SYSTEM.
- `app.css` defines the light tokens on `:root` and the dark tokens under both `:root[data-theme="dark"]` and `@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) { … } }`. It maps `--accent: var(--accent-l)` or `var(--accent-d)` accordingly.
- It also sets `color-scheme: light` or `dark` so that native controls and scrollbars match.
- Both the light and dark variants are always emitted, because under SYSTEM the browser picks at paint time.
- Theme and color choices apply on the next page load after a normal form POST with a redirect (PRG). No JavaScript is involved.
- *Why:* The first byte of HTML already carries the right theme, so a flash is impossible, and no script is needed.
- *Alternative:* A per-user stylesheet endpoint (`/appearance.css?v=…`). This is CSP-friendlier (no inline style), but it adds a render-blocking request and a cache-invalidation concern. Kept as the fallback if a CSP is introduced (see Risks).
- The only values that ever go into the `style` attribute are hex strings the server computed from a validated color. Raw user input is never interpolated.

### 7. Contrast-safe derived colors (`AccentColors`)

A small utility in `app.adventr.account` computes WCAG 2.x relative luminance and contrast ratios. From one picked color, it derives three values **per theme**:

| Variant | Used for | Rule |
|---|---|---|
| `accent` | Primary button fill, selected states, and the brand mark | ≥ 3:1 against both `--bg` and `--surface` of that theme (a non-text UI component) |
| `accent-fg` | Text and icons on `accent` | Black or white, whichever contrasts more with `accent`. The adjustment of `accent` keeps going until this is ≥ 4.5:1 |
| `accent-text` | Links, accent-colored text, and the **focus ring** | ≥ 4.5:1 against both `--bg` and `--surface` |

- **Adjustment:** Convert to OKLCH. Keep hue and chroma, and step lightness toward black (light theme) or white (dark theme) in small increments until the rule holds. Reduce chroma only if a step leaves the sRGB gamut.
- **Termination:** At L = 0 or L = 1 the color is black or white, which clears every threshold against the fixed backgrounds, so the adjustment always terminates.
- *Why OKLCH over HSL:* Its lightness is perceptually uniform. An adjusted yellow still looks like a darker yellow and doesn't turn olive or brown the way it does in HSL.
- The focus ring uses `accent-text`, so it meets 3:1 automatically (it's ≥ 4.5:1).
- Results are computed per request. This takes microseconds, with no caching and no stored derived values. If the backgrounds are retuned later, every user's colors follow automatically.
- **Presets:** About 8 hand-picked hues, e.g. green (default), teal, blue, indigo, purple, rose, red, and orange. A unit test asserts that each preset needs no adjustment in either theme, or only a minimal one.

### 8. Live preview via htmx, no custom JavaScript

- The appearance form has the preset swatches (radio buttons styled as swatches, each with a visible name for screen readers and non-color identification) and a native `<input type="color">`.
- `hx-get="/account/appearance/preview"` with `hx-trigger="input changed delay:150ms"` returns a preview fragment. The fragment contains a sample button, link, and focus ring in both light and dark mode, rendered with the derived colors. It also shows a line such as "Adjusted for readability" when the derived color differs from the picked one.
- Nothing is saved until the user submits.

### 9. Design tokens

`app.css` is refactored so that **no color literal appears outside the token blocks**. Planned token set:

- **Surfaces and text:** `--bg`, `--surface`, `--surface-2`, `--fg`, `--muted`
- **Borders:** `--border` (decorative), `--border-strong` (input boundaries, ≥ 3:1)
- **Accent:** `--accent`, `--accent-fg`, `--accent-text`, `--focus-ring`
- **Danger:** `--danger`, `--danger-fg`, `--danger-text`
- **Flash colors:** `--notice-bg`, `--notice-fg`, `--error-bg`, `--error-fg`
- **Disabled:** `--disabled-fg`, `--disabled-bg`

Each token has a light and a dark value.

- **Focus:** A global `:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }` covers every interactive element, including `summary` (the group switcher), swatches, and the reaction buttons.
- **Hover:** Hover states change more than hue, e.g. an underline or a darker border, so that they are not color-only.

### 10. The audit is automated where possible, manual where necessary

- **Automated:** A `TokenContrastTests` unit test parses the token blocks of `app.css` for both themes. It asserts a declared list of foreground/background pairs, e.g. `--fg`/`--bg`, `--muted`/`--surface`, `--error-fg`/`--error-bg`, `--border-strong`/`--surface`, and `--danger-text`/`--bg`, against 4.5:1 for text and 3:1 for UI components. This makes future CSS changes fail the build instead of regressing silently.
- **Automated:** `AccentColorsTests` checks 1,000 random colors plus edge cases (pure yellow, pure blue, black, white, and near-background colors) and asserts all thresholds in both themes.
- **Manual:** A checklist walk-through of every screen in light and dark mode, with the default color, pure yellow `#ffff00`, and a very dark navy `#0a0a40`. Tools: the browser's accessibility inspector, plus a free axe-core browser extension, used as tools rather than dependencies. Also a keyboard-only pass: tab through each page and check that the focus is always visible. The results are recorded in `docs/accessibility.md`.

## Risks / Trade-offs

- **The delta spec targets the MVP's `user-auth` spec, which exists only after the MVP is archived** → The proposal states the prerequisite. Archive `add-friend-adventures-mvp` before this change is archived, or its delta has nothing to modify.
- **Flyway version numbers depend on the MVP ending at V6** → Use the next free version at implementation time, probably `V7__account_and_appearance.sql`.
- **The inline `style` on `<html>` conflicts with a future strict CSP** → If a CSP is added, allow `style-src-attr 'unsafe-inline'`, which is low-risk because only server-computed hex values are emitted, or switch to the per-user stylesheet endpoint (Decision 6, alternative).
- **Auto-adjusted colors may look different from what the user picked** → The live preview shows the adjusted result and says so explicitly. The picked value is stored unchanged, so tweaking the backgrounds later never loses the user's choice.
- **Initials for former members lose the avatar in old comments** → An accepted trade-off. Privacy follows current membership, and the name and "former member" label remain.
- **The avatar check adds a query per avatar request** → Avatars are served with long-lived private caching (versioned URL), and the `exists` query hits indexed membership columns. At this scale it's negligible.
- **The OIDC principal used for "reset to Keycloak name" is from session start** → If the Keycloak name changed during the session, the reset uses the older value until the next login. The next login then syncs it anyway, because the flag is false.
- **The contrast audit is partly manual and can drift** → `TokenContrastTests` and `AccentColorsTests` guard the systematic part. The manual checklist is repeated at the end of any future change that adds screens.

## Migration Plan

1. Deploy as a normal app release. The Flyway migration only adds columns with safe defaults (`display_name_custom = false`, `theme = 'SYSTEM'`, and nulls). Existing users see no change until they visit My account, except that the new dark tokens apply automatically to users whose OS is in dark mode, because System is the default.
2. The avatar directory is created on first upload inside the existing images volume. Backups already include that volume.
3. Rollback: redeploy the previous image. The extra columns are ignored by the old code, and the old upsert simply resumes overwriting custom names.

## Open Questions

- **Default theme for existing users:** System (as planned), or Light to avoid surprising existing dark-OS users with the new dark theme on release day? The default is System, and this can be confirmed at implementation.
- **Exact preset hues:** Final hex values are chosen during implementation against the finished dark palette and verified by `AccentColorsTests`.
