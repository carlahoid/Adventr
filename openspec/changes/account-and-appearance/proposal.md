## Why

Once the MVP is live, friends can use Adventr but cannot make it feel like their own. Their name is whatever Keycloak derived from registration, there are no faces next to names, and the app has one fixed light look. We also have not yet checked the colors systematically for readability. This change adds a personal "My account" page with a profile and appearance settings. It also makes the whole UI themeable and readable in light and dark, with any accent color a user picks.

It is the first follow-up after MVP go-live (`add-friend-adventures-mvp`, groups 1–9), so it builds on and audits the finished adventure, reaction, and comment screens.

## What Changes

- New **My account** page (`/account`), reachable from the header. It has three sections: Profile, Appearance, and Account.
- **Profile:**
  - An editable display name that survives later logins, with a way to go back to the Keycloak name.
  - An optional short bio.
  - An optional avatar (one uploaded image, processed like adventure images).
  - Only the user can view and edit their own account page. Other people see only your avatar, display name, and bio, and only in places where they already see you: the member list, author labels, and the header for yourself. There are **no public profile pages**.
- **Avatar display:** in the header, the group member list, and author/reactor labels. If no avatar is set, an initials placeholder is shown. Avatar images are served only to the user and to people who currently share a group with them.
- **Appearance:**
  - Theme: Light, Dark, or System (the default).
  - Primary color: one of about 8 curated presets or any custom color.
  - Both are saved on the user's account, so they follow the user across devices. They are rendered on the server, so the page never flashes the wrong theme.
- **Contrast-safe custom colors:** For any chosen color, the server derives per-theme variants: button fill, text on the button, and link/accent text. These reach WCAG AA contrast while keeping the chosen hue. A live preview on the account page shows the adjusted result.
- **CSS token refactor:** Every hardcoded color in `app.css` becomes a design token, and a complete dark token set is added.
- **Contrast and focus audit** of every screen in light and dark mode, including extreme custom colors. It covers text, controls, links, inputs, cards, navigation, flash messages, hover, focus, and disabled states. Every interactive element gets a visible keyboard focus indicator, and no information is conveyed by color alone.
- **Account section:** email, password, and linked Google login stay in Keycloak. The page links to the Keycloak account console instead of rebuilding those screens.
- **Login sync change:** Login no longer overwrites a display name the user has customized. Email and the Keycloak-derived name still sync.
- **Constraint (no code):** The app stays completely free. No feature may require payment, a subscription, or a paid service.
- **Not in this change:**
  - Public profiles and a "my adventures" list. Adventures stay group-owned as specified in the MVP.
  - Google login. It is enabled at go-live as an MVP deployment task, with no app code.
  - Per-group themes.
  - Uploading more than one avatar.

## Capabilities

### New Capabilities
- `account-profile`: The My account page, editable display name with a reset, bio, and avatar upload/removal. Covers the self-only access rule, where avatar, name, and bio appear to others, the protected avatar endpoint, the initials fallback, and the link to the Keycloak account console.
- `appearance`: Theme (light/dark/system) and primary color (presets plus custom). Covers per-user persistence, flash-free server-side rendering, derived contrast-safe color variants and the live preview, the design-token/dark-theme requirement, and the accessibility requirements: contrast thresholds, visible focus, and no color-only information.

### Modified Capabilities
- `user-auth`: "Local user provisioning" changes. The display name is refreshed from Keycloak on login **only while the user has not customized it**. Email is still always refreshed.

## Impact

- **Code:**
  - New package `app.adventr.account`: controller, service, contrast/color derivation, and avatar storage.
  - Changes to `UserService`/`UserRepository` (the upsert keeps custom names), `LayoutModelAdvice` (theme and colors for the layout), `layout.html`, `fragments/header.html`, `fragments/author.html`, `groups/settings.html`, and `app.css`, which is largely rewritten to use tokens and gains a dark theme.
  - Reuses the MVP image processing pipeline and the membership guard.
- **Data:** One Flyway migration that adds to `users`: `display_name_custom`, `bio`, `avatar_path`, `theme`, and `accent_color`.
- **Storage:** Avatar files go on the existing images volume under `/data/images/avatars/`, so they are covered by the existing nightly backup.
- **Dependencies:** None. Contrast math is a small in-house utility, and the color picker is native `<input type="color">` plus htmx.
- **Systems:** No change to Keycloak, Compose, or Caddy.
- **Prerequisite:** `add-friend-adventures-mvp` is implemented and archived, so its `user-auth` spec exists in `openspec/specs/`.
