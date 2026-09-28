## ADDED Requirements

### Requirement: Theme selection
A user SHALL be able to choose Light, Dark, or System as their theme on the account page. System SHALL be the default and SHALL follow the operating system's color-scheme preference. The choice SHALL be stored on the user's account, so that it applies on every device where they are logged in.

#### Scenario: User switches to dark mode
- **WHEN** a user selects "Dark" and saves
- **THEN** every page renders with the dark theme, including after logging out and in again on another device

#### Scenario: System theme follows the OS
- **WHEN** a user with the theme "System" views the app on a device set to dark mode
- **THEN** the app renders with the dark theme, and it renders light on a device set to light mode

#### Scenario: Logged-out visitor
- **WHEN** a logged-out visitor opens the landing page
- **THEN** the page uses the System theme and the default primary color

### Requirement: No theme flash
The theme and primary color SHALL be applied in the initial HTML response, so that the first paint already uses the correct colors. It SHALL NOT depend on client-side script.

#### Scenario: Page load in dark mode
- **WHEN** a user with the theme "Dark" loads any page, including with JavaScript disabled
- **THEN** the page is painted in the dark theme from the first frame, without a light flash

### Requirement: Primary color selection
A user SHALL be able to choose their primary color from a set of named presets, or pick any custom color. The chosen color SHALL be stored on the user's account. The system SHALL accept only a six-digit hex color (`#rrggbb`) and reject any other value. The primary color SHALL be applied consistently to primary buttons, links, selected and interactive states, navigation highlights, the brand mark, and focus indicators.

#### Scenario: User picks a preset
- **WHEN** a user selects the preset "Blue" and saves
- **THEN** primary buttons, links, and focus rings across the app use the blue accent

#### Scenario: User picks a custom color
- **WHEN** a user picks `#c2185b` in the color picker and saves
- **THEN** the app uses that hue as the primary color on every page

#### Scenario: Invalid color value
- **WHEN** a request submits the color value `red;background:url(x)` or `#12345`
- **THEN** the system rejects it with an error and keeps the previous color

#### Scenario: Back to default
- **WHEN** a user clicks "Reset to default color"
- **THEN** the app uses the default primary color again

### Requirement: Contrast-safe derived colors
For any stored primary color, the system SHALL derive the colors actually used in each theme so that they keep the chosen hue and meet these thresholds against the theme's page and card backgrounds:
- The primary fill (buttons, selected states) SHALL reach at least 3:1.
- Text and icons on the primary fill SHALL reach at least 4.5:1 against the fill.
- Links, accent-colored text, and focus indicators SHALL reach at least 4.5:1.

The stored color SHALL remain the user's original choice.

#### Scenario: Very light custom color in light mode
- **WHEN** a user picks `#ffff00` and uses the light theme
- **THEN** links and buttons use a darkened yellow that meets the thresholds, and button text is black

#### Scenario: Very dark custom color in dark mode
- **WHEN** a user picks `#0a0a40` and uses the dark theme
- **THEN** links and buttons use a lightened navy that meets the thresholds against the dark background

#### Scenario: Preset colors need no correction
- **WHEN** any preset is used in the light or the dark theme
- **THEN** all thresholds are met

### Requirement: Appearance preview
While choosing a color, the account page SHALL show a live preview of a primary button, a link, and a focus indicator in both light and dark themes, using the derived colors. When the derived colors differ noticeably from the picked color, the preview SHALL say that the color was adjusted for readability. Nothing SHALL be saved until the user submits the form.

#### Scenario: Previewing a hard-to-read color
- **WHEN** a user moves the color picker to `#ffff00`
- **THEN** the preview updates to show the adjusted colors and the note "Adjusted for readability", and the stored color does not change until they save

### Requirement: Themeable design tokens
All colors in the application's stylesheet SHALL be defined as design tokens with a value for the light theme and a value for the dark theme. No component style SHALL use a color literal directly.

#### Scenario: Card in dark mode
- **WHEN** a page with cards, inputs, flash messages, and a danger button is shown in the dark theme
- **THEN** all of them use dark-theme token values, and none keeps a light-only color such as a white background

### Requirement: Color contrast
In both themes and with any primary color, the application SHALL meet WCAG 2.x level AA contrast:
- Normal text: at least 4.5:1 against its background.
- Large text, UI component boundaries (including text inputs), focus indicators, and meaningful graphics: at least 3:1.

This SHALL hold for default, hover, focus, selected, and error states. Disabled controls are exempt from the ratio, but SHALL remain recognizable as disabled by more than color.

#### Scenario: Input boundaries
- **WHEN** a text input is shown on a card in either theme
- **THEN** its border reaches at least 3:1 against the card background

#### Scenario: Muted text
- **WHEN** secondary (muted) text is shown on the page background or a card in either theme
- **THEN** it reaches at least 4.5:1

#### Scenario: Stylesheet regression
- **WHEN** a change to the stylesheet lowers a declared foreground/background token pair below its threshold
- **THEN** the automated contrast test fails the build

### Requirement: Visible keyboard focus
Every interactive element SHALL show a clearly visible focus indicator when focused with the keyboard, in both themes and with any primary color. This includes links, buttons, inputs, color swatches, the group switcher, and the reaction and comment controls.

#### Scenario: Keyboard navigation
- **WHEN** a user moves through any page with the Tab key
- **THEN** the focused element always shows a focus ring of at least 3:1 contrast against its surroundings

### Requirement: No color-only information
The application SHALL NOT convey information by color alone. States such as errors, success, the user's own reaction, selected presets, adventure status, and hover SHALL also be indicated by text, an icon, shape, weight, or an accessible attribute.

#### Scenario: Own reaction
- **WHEN** a user has reacted 👍 to an adventure
- **THEN** their reaction is indicated by more than a color change (e.g. a pressed state exposed as `aria-pressed="true"` plus a visual weight or outline change)

#### Scenario: Selected preset
- **WHEN** a user views the preset swatches
- **THEN** each swatch has a visible or accessible name, and the selected one is marked by a check mark or outline, not only by its color
