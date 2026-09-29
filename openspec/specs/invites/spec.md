# invites Specification

## Purpose
TBD - created by archiving change add-friend-adventures-mvp. Update Purpose after archive.
## Requirements
### Requirement: Generate invite link
The Owner SHALL be able to generate an invite link of the form `/join/{token}` for their group. The token SHALL be unguessable (at least 128 bits of randomness). Each group SHALL have at most one active invite link. The link SHALL expire 7 days after generation.

#### Scenario: Owner generates link
- **WHEN** the Owner clicks "Create invite link" on the settings page
- **THEN** the system shows a copyable link with its expiry date

#### Scenario: Member cannot generate link
- **WHEN** a Member requests invite generation
- **THEN** the system rejects it with HTTP 403

### Requirement: Invite link visibility
Any active member SHALL be able to see and copy the current invite link and its expiry, so that they can share it.

#### Scenario: Member copies link
- **WHEN** a Member opens the group settings page while a valid invite exists
- **THEN** they see the link and a copy button

### Requirement: Reusable link
An invite link SHALL be usable by any number of people until it expires or is regenerated.

#### Scenario: Several friends join with the same link
- **WHEN** three different users open the same valid invite link
- **THEN** all three become Members of the group

### Requirement: Regenerate invite link
The Owner SHALL be able to regenerate the invite link. Regenerating SHALL issue a new token with a fresh expiry and immediately invalidate the previous token.

#### Scenario: Old link after regeneration
- **WHEN** the Owner regenerates the link and someone then opens the old link
- **THEN** the system shows "This invite link is no longer valid" and does not add them to the group

### Requirement: Join flow
Visiting `/join/{token}` SHALL require authentication. Unauthenticated visitors SHALL be sent through Keycloak login or registration and then returned to the join page. After authentication, a valid token SHALL add the user to the group as a Member and redirect them to the group's adventure list.

#### Scenario: New user joins via link
- **WHEN** a visitor without an account opens a valid invite link, registers on Keycloak, and returns
- **THEN** a local user is created, they become a Member of the group, and they land on `/groups/{id}`

#### Scenario: Existing user joins via link
- **WHEN** a logged-in user who is not a member opens a valid invite link
- **THEN** the join page shows the group name and a "Join group" button, and confirming adds them as a Member

#### Scenario: Already a member
- **WHEN** an active member opens a valid invite link for their own group
- **THEN** they are redirected to the group's adventure list and no duplicate membership is created

#### Scenario: Former member rejoins
- **WHEN** a user who previously left opens a valid invite link
- **THEN** their membership is reactivated as a Member with a new join date

#### Scenario: Expired link
- **WHEN** a user opens an invite link past its expiry
- **THEN** the system shows "This invite link has expired, ask the group for a new one" and does not add them

