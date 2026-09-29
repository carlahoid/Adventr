# Accessibility: contrast and focus audit

Adventr aims for **WCAG 2.x level AA** in both themes and with any primary color:

- **Contrast:**
  - Normal text needs at least 4.5:1.
  - Large text, UI component boundaries (including text inputs), focus indicators, and meaningful graphics need at least 3:1.
  - This holds in the default, hover, focus, selected, and error states.
- **Visible focus:** every interactive element shows a visible keyboard focus indicator.
- **No color-only information:** no information is conveyed by color alone.

This document holds the audit checklist and its results. Repeat the audit at the end of any change that adds screens.

## What is automated

| Test | Guards |
|---|---|
| `TokenContrastTests` | Every declared foreground/background token pair in `app.css`, in both themes: text 4.5:1, UI and focus 3:1. No color literal outside the token blocks. Both dark blocks are identical. `AccentColors` uses the stylesheet's `--bg`, `--surface`, and `--fg`. |
| `AccentColorsTests` | 1,000 random colors plus edge cases (`#ffff00`, `#0000ff`, black, white, the backgrounds themselves) meet all accent thresholds in both themes. Presets are unchanged in light mode, and only lightened in dark mode. |
| Integration tests | `aria-pressed` on the own reaction and on the current status. The check mark and name on the selected preset. `data-theme` and the inline colors rendered on the server. |

**Declared pairs:**

- Text (4.5:1):
  - `--fg`, `--muted` on `--bg`, `--surface`, `--surface-2`
  - `--accent-text`, `--danger-text` on `--bg`, `--surface`
  - `--accent-fg` on `--accent`, `--danger-fg` on `--danger`
  - `--notice-fg` on `--notice-bg`, `--error-fg` on `--error-bg`
- UI and focus (3:1): `--border-strong`, `--accent`, `--focus-ring`, and `--danger` on `--bg` and `--surface`

**Where to add new pairs:** in the `TEXT_PAIRS` or `UI_PAIRS` lists of `TokenContrastTests`.

## Manual checklist

**Setup:**

1. Run the app with the dev stack (see the README).
2. Use two accounts that share a group containing adventures with images, reactions (including your own), comments (your own and someone else's), and an edited comment.
3. **Tools:**
   - The browser's accessibility inspector (Chrome DevTools → Elements → Accessibility; Firefox → Accessibility).
   - A free axe-core browser extension (e.g. "axe DevTools"), used as a tool, not a dependency.
   - The keyboard only (Tab, Shift+Tab, Enter, Space, arrow keys in radio groups).
4. **Switching themes:** use My account → Appearance (Light, Dark). For System, also switch the OS setting.

**Combinations:** every screen × light and dark × three primary colors: the default green, pure yellow `#ffff00` (the worst case in light mode), and very dark navy `#0a0a40` (the worst case in dark mode).

**For each screen, check:**

- Text, muted text, links, and buttons (primary, secondary, danger, and link-style) reach the contrast minimum.
- Inputs, cards, and navigation, including the group switcher and its open menu.
- Flash messages (notice and error), hover states, focus rings, and disabled states.

### Screens

| # | Screen | URL | Notes |
|---|---|---|---|
| 1 | Landing (logged out) | `/` | System theme, default color |
| 2 | My groups | `/groups` | Empty state and list; create-group form with an error |
| 3 | Group adventure list | `/groups/{id}` | Sections, thumbnails, reaction bar (own reaction), quick add with an error |
| 4 | Adventure detail | `/groups/{id}/adventures/{aid}` | Status control (current status), image, reactor names, former-member label |
| 5 | Adventure new/edit | `…/adventures/new`, `…/edit` | Field errors, image upload and remove |
| 6 | Comments | Detail page, `#comments` | Post, edit form, "(edited)", delete; errors |
| 7 | Reactions | List and detail | Toggle 👍/👎; `aria-pressed`; tooltip names |
| 8 | Group settings | `/groups/{id}/settings` | Member list with avatars/initials and bio, owner badge, invite link, danger zone |
| 9 | Join | `/join/{token}` | Valid, expired (410), and invalid (404) |
| 10 | My account | `/account` | Profile (errors), avatar, appearance (swatches, custom color, preview, adjusted note), account link |
| 11 | Error pages | e.g. `/groups/999999` (404), a 403 | Rendered in the user's theme |

### Results

Mark each cell: ✅ no findings, ⚠ finding (describe it below), – not applicable.

| # | Light · green | Light · `#ffff00` | Light · `#0a0a40` | Dark · green | Dark · `#ffff00` | Dark · `#0a0a40` | Keyboard |
|---|---|---|---|---|---|---|---|
| 1 | | | | | | | |
| 2 | | | | | | | |
| 3 | | | | | | | |
| 4 | | | | | | | |
| 5 | | | | | | | |
| 6 | | | | | | | |
| 7 | | | | | | | |
| 8 | | | | | | | |
| 9 | | | | | | | |
| 10 | | | | | | | |
| 11 | | | | | | | |

### Keyboard pass

For each screen:

- Tab through everything. Focus is always visible, never lost to `<body>`, and moves in a logical order (header → content → forms).
- The group switcher (`<details>`) opens with Enter or Space, and its links are reachable.
- After htmx swaps, focus stays or lands sensibly:
  - **Reaction bar:** stays on the clicked button (stable ids).
  - **Quick add:** back in the title field.
  - **Comment post:** stays in the comment box.
  - **Comment save or cancel:** moves to the comment's "Edit" link.
  - **Comment delete:** moves to the "Comments (n)" heading.
  - **Appearance preview:** stays in the color control.

## Findings from the code review (before the manual walkthrough)

Reviewed on 2026-09-29 while implementing `account-and-appearance`. All of these are fixed:

| Finding | Fix |
|---|---|
| Text inputs had only the decorative `--border` (about 1.3:1 on white) | Inputs, secondary buttons, the status control, and swatches use `--border-strong` (≥ 3:1, tested) |
| No focus styles | A global `:focus-visible` outline in `--focus-ring`, which is the accent text color (≥ 4.5:1, so ≥ 3:1 is guaranteed with any color) |
| Hover changed nothing, or only color | Buttons get an underline, links a thicker one, and reaction buttons a visible border |
| Flash messages were told apart only by background color | ✓/⚠ icon prefix, plus "Error:" for screen readers; `role="status"` or `role="alert"` |
| Field errors were marked only by red text | ⚠ icon (CSS, hidden from the accessible name), `aria-invalid`, and `aria-describedby` pointing at the message |
| The own reaction was only highlighted in green | `aria-pressed`, bold, and an outline (implemented with the reactions) |
| The badge and own reaction used a green-only tint that wouldn't match other accents | A neutral `--surface-2` background with `--fg` text, plus a border |
| Links used the browser's default blue in every theme | `--accent-text` (≥ 4.5:1 on page and card in both themes) |
| Focus was lost after deleting a comment, or after saving or cancelling an edit | Focus moves to the thread heading, or to the comment's "Edit" link |
| Placeholder color was left to the browser | `--muted` (tested pair) |

## Accepted exceptions

None yet. Record here any finding that is deliberately not fixed, with the reason.
