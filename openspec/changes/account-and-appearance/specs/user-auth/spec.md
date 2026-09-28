## MODIFIED Requirements

### Requirement: Local user provisioning
On each successful login, the system SHALL ensure a local `User` record exists, keyed by the Keycloak `sub` claim. The record SHALL be created on first login. Its email SHALL be refreshed from the token on every login. Its display name SHALL be refreshed from the token on every login **unless the user has set a custom display name** in the app. In that case, the custom name SHALL be kept.

#### Scenario: First login creates local user
- **WHEN** a user logs in for the first time
- **THEN** the system creates a `User` row with their `sub`, display name, and email

#### Scenario: Subsequent login updates profile
- **WHEN** a user whose display name changed in Keycloak, and who has not set a custom display name, logs in again
- **THEN** the system updates the stored display name and does not create a second `User` row

#### Scenario: Custom display name survives login
- **WHEN** a user who set the custom display name "Kim the Climber" logs in again after their Keycloak name changed
- **THEN** the system keeps "Kim the Climber" as the display name and refreshes only the email

#### Scenario: Name shown right after login
- **WHEN** a user with a custom display name logs in
- **THEN** the header shows the custom display name on the first page after login
