## ADDED Requirements

### Requirement: My account page
The system SHALL provide a "My account" page at `/account` for the logged-in user. It SHALL have Profile, Appearance, and Account sections. The header SHALL link to it from the user's name. The page SHALL always show and edit the account of the logged-in user, and no account route SHALL accept a user id.

#### Scenario: User opens their account page
- **WHEN** a logged-in user clicks their name in the header
- **THEN** the system shows `/account` with their current display name, bio, avatar, theme, and primary color

#### Scenario: Unauthenticated access
- **WHEN** an unauthenticated visitor requests `/account`
- **THEN** the system redirects them to the Keycloak login page

#### Scenario: No way to address another user's account
- **WHEN** a logged-in user submits an account form with a tampered hidden field or query parameter naming another user's id
- **THEN** the system ignores it and changes only the logged-in user's own account

### Requirement: Editable display name
A user SHALL be able to set their own display name, 1–60 characters after trimming, with no control characters. The new name SHALL immediately appear everywhere the user is shown: the header, the group member lists, and the author and reactor labels.

#### Scenario: User changes their display name
- **WHEN** a user saves the display name "Kim the Climber" on the account page
- **THEN** the system stores it, shows a success message, and co-members see "Kim the Climber" in the member list and on the user's adventures and comments

#### Scenario: Invalid display name
- **WHEN** a user submits an empty display name, a name of only spaces, or a name longer than 60 characters
- **THEN** the system rejects it with a field error and keeps the previous name

### Requirement: Reset display name to the Keycloak name
A user who has customized their display name SHALL be able to switch back to the name derived from their Keycloak account. After a reset, later logins SHALL keep the name in sync with Keycloak again.

#### Scenario: User resets their name
- **WHEN** a user with a custom display name clicks "Use my Keycloak name"
- **THEN** the system shows the name derived from their Keycloak account, and the next login refreshes it from Keycloak

### Requirement: Bio
A user SHALL be able to set an optional plain-text bio of up to 160 characters, and to clear it. Line breaks SHALL be collapsed to spaces. The bio SHALL be rendered escaped. It SHALL be shown only to the user on their account page and to their current co-members next to the user in the group member list.

#### Scenario: User sets a bio
- **WHEN** a user saves the bio "Always up for a hike"
- **THEN** co-members see "Always up for a hike" next to the user's name in the group member list

#### Scenario: Bio with markup
- **WHEN** a user saves the bio `<script>alert(1)</script>`
- **THEN** the bio is displayed as literal text and no script runs

#### Scenario: Bio too long
- **WHEN** a user submits a bio longer than 160 characters
- **THEN** the system rejects it with a field error and keeps the previous bio

### Requirement: Avatar upload and removal
A user SHALL be able to upload one avatar image (JPEG, PNG, or WebP, at most 5 MB) and to remove it. The system SHALL verify the file type by its content, apply the EXIF orientation, strip metadata, center-crop it to a square, resize it to 256 × 256 px, and store it as a re-encoded JPEG. Replacing or removing an avatar SHALL delete the previous file.

#### Scenario: User uploads an avatar
- **WHEN** a user uploads a 3000 × 2000 px JPEG photo as their avatar
- **THEN** the system stores a 256 × 256 px JPEG without EXIF metadata and shows it on the account page and in the header

#### Scenario: Rejected upload
- **WHEN** a user uploads a PDF renamed to `.jpg`, or an image larger than 5 MB
- **THEN** the system rejects it with an error message and keeps the previous avatar

#### Scenario: User replaces their avatar
- **WHEN** a user with an avatar uploads a new one
- **THEN** the new avatar is shown and the previous file is deleted from storage

#### Scenario: User removes their avatar
- **WHEN** a user clicks "Remove avatar"
- **THEN** the avatar file is deleted and the initials placeholder is shown instead

### Requirement: Avatar visibility limited to co-members
Avatar images SHALL be served only through an application endpoint. They SHALL be visible only to the avatar's owner and to users who currently share an active group membership with the owner. For anyone else, the endpoint SHALL respond with 404. Responses SHALL use private caching only.

#### Scenario: Co-member sees an avatar
- **WHEN** a user requests the avatar of someone who is an active member of one of their groups
- **THEN** the system returns the image with a `Cache-Control: private` header

#### Scenario: Stranger cannot fetch an avatar
- **WHEN** a logged-in user requests the avatar of someone they share no active group with
- **THEN** the system responds with 404

#### Scenario: Former co-member
- **WHEN** a user left the only group they shared with another user, and then requests that user's avatar
- **THEN** the system responds with 404, and pages show the initials placeholder for that user

### Requirement: Avatar display with initials fallback
The system SHALL show a user's avatar next to their name in the header (the user themself), in the group member list, and in the author and reactor labels. If a user has no avatar, or the viewer may not see it, the system SHALL show a placeholder with the user's initials. The name SHALL always be shown as text next to the avatar or placeholder, so the image is never the only identification.

#### Scenario: User without avatar
- **WHEN** a member without an avatar appears in the member list
- **THEN** the list shows an initials placeholder followed by their display name

### Requirement: Keycloak account link
The Account section SHALL link to the Keycloak account console, where the user manages their email, password, and linked Google login. The app SHALL NOT offer its own forms for these.

#### Scenario: User wants to change their password
- **WHEN** a user clicks "Manage email, password and sign-in" on the account page
- **THEN** they are taken to the Keycloak account console of the `adventr` realm

### Requirement: Free to use
No account, profile, or appearance feature SHALL require payment, a subscription, or a paid third-party service.

#### Scenario: All features available to every user
- **WHEN** any registered user opens the account page
- **THEN** every feature on it is available without payment or any upgrade prompt
