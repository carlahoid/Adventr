## ADDED Requirements

### Requirement: Keycloak OIDC login
The system SHALL authenticate users exclusively through Keycloak using OpenID Connect (authorization code flow). Every page except the public landing page and static assets SHALL require authentication.

#### Scenario: Unauthenticated access redirects to Keycloak
- **WHEN** an unauthenticated visitor requests `/groups`
- **THEN** the system redirects them to the Keycloak login page of the `adventr` realm

#### Scenario: Successful login returns to the requested page
- **WHEN** a user completes login on Keycloak after being redirected from `/groups/42`
- **THEN** the system establishes a session and redirects them back to `/groups/42`

### Requirement: Self-registration
The Keycloak realm SHALL allow visitors to register a new account themselves with a username, email, and password.

#### Scenario: New visitor registers
- **WHEN** a visitor clicks "Register" on the Keycloak login page and submits valid details
- **THEN** Keycloak creates the account and the user is logged in to the app

### Requirement: Password reset
The Keycloak realm SHALL offer a "Forgot password" flow that sends a reset email through a configured SMTP relay.

#### Scenario: User resets forgotten password
- **WHEN** a registered user requests a password reset for their email address
- **THEN** Keycloak sends a reset link to that address, and the user can set a new password with it

### Requirement: Optional Google login
The realm SHALL support Google as an identity provider when a Google OAuth client ID and secret are configured. Without that configuration, the Google option SHALL NOT be shown.

#### Scenario: Google configured
- **WHEN** Google credentials are configured and a visitor chooses "Sign in with Google"
- **THEN** the visitor is authenticated through Google and logged in to the app

#### Scenario: Google not configured
- **WHEN** no Google credentials are configured
- **THEN** the login page shows no Google option

### Requirement: Local user provisioning
On each successful login, the system SHALL ensure a local `User` record exists, keyed by the Keycloak `sub` claim. The record SHALL be created on first login, and its display name and email SHALL be refreshed from the token on every login.

#### Scenario: First login creates local user
- **WHEN** a user logs in for the first time
- **THEN** the system creates a `User` row with their `sub`, display name, and email

#### Scenario: Subsequent login updates profile
- **WHEN** a user whose display name changed in Keycloak logs in again
- **THEN** the system updates the stored display name and does not create a second `User` row

### Requirement: Logout
The system SHALL provide a logout action that ends both the app session and the Keycloak SSO session (RP-initiated logout).

#### Scenario: User logs out
- **WHEN** a logged-in user clicks "Log out"
- **THEN** the app session is invalidated, the Keycloak session is ended, and the next visit to `/groups` requires a new login

### Requirement: CSRF protection
All state-changing requests (POST, PUT, PATCH, DELETE), including htmx requests, SHALL require a valid CSRF token.

#### Scenario: Request without CSRF token
- **WHEN** an authenticated client sends a POST without a valid CSRF token
- **THEN** the system rejects it with HTTP 403 and changes no data
