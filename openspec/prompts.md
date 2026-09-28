# Change: User Accounts, Adventures & Appearance

## Context

This is an existing web application. Extend the current application rather than rebuilding or replacing existing functionality.

The goal is to make the core experience accessible to everyone for free while adding user accounts, user-created adventures, profiles, and appearance customization.

Before making implementation decisions, inspect the existing codebase, architecture, authentication, database/schema, UI components, styling system, and existing adventure functionality.

## Goals

* Add Google login while preserving the existing authentication system.
* Allow authenticated users to create and manage their own adventures.
* Add user profile pages and profile editing.
* Add appearance settings for light/dark mode and primary color.
* Audit and fix color contrast throughout the application.
* Keep the core website free and accessible to everyone.
* Preserve existing functionality and follow the project's established patterns.

## Requirements

### 1. Google Authentication

Add Google OAuth login to the existing authentication system.

#### Requirements

* Inspect the existing authentication implementation first.
* Integrate Google authentication into the existing auth architecture.
* Do not introduce a second authentication system if the current provider already supports Google OAuth.
* Preserve existing authentication methods.
* Support login, logout, loading, and error states.
* Store only the user data required by the application.
* Never commit OAuth credentials or secrets.
* Use environment variables for required credentials.
* Document all required configuration.

#### Acceptance criteria

* A user can sign in using Google.
* Existing authentication continues to work.
* A Google user receives/updates the appropriate application user record.
* Authentication state is available consistently throughout the application.
* Invalid/cancelled authentication attempts are handled gracefully.
* No secrets are committed to the repository.

---

### 2. Adventures

Authenticated users must be able to create and manage their own adventures.

#### Requirements

Users should be able to:

* Create an adventure.
* Provide a title.
* Provide a description.
* Add images/media if the existing application supports media uploads.
* Use any existing adventure-specific fields.
* Save an adventure.
* View their adventures.
* Edit their own adventures.
* Delete their own adventures where appropriate.

#### Authorization

* Users can create adventures belonging to themselves.
* Users can edit only their own adventures.
* Users can delete only their own adventures.
* Authorization must be enforced server-side/backend-side where applicable.
* Do not rely solely on frontend checks.

#### Acceptance criteria

* An authenticated user can successfully create an adventure.
* Newly created adventures are associated with the correct user.
* Users can see their own adventures.
* Users can edit their own adventures.
* Users cannot edit another user's adventure.
* Users cannot delete another user's adventure.
* Invalid input is rejected appropriately.
* Loading, empty, success, and error states are handled.

---

### 3. User Profiles

Add a profile page for every user.

#### Profile data

Where appropriate for the existing application, support:

* Avatar/profile image.
* Display name.
* Username.
* Short bio.
* User's adventures.
* Basic account information.

Users must be able to edit their own profile.

#### Acceptance criteria

* Every authenticated user has a profile.
* A user can view their profile.
* A user can edit their own profile.
* A user cannot modify another user's profile.
* The user's adventures can be displayed on their profile.
* The profile is responsive.
* The profile follows the existing application's visual language.

---

### 4. Appearance

Add an Appearance section to the application settings.

#### Theme

Support:

* Light mode.
* Dark mode.
* System/default mode if compatible with the existing architecture.

The selected theme must persist across sessions.

Avoid visible theme flashing during initial page load where technically possible.

#### Primary color

Allow users to select/change the application's primary color.

The implementation should use the existing design-token/CSS-variable system if one exists.

The selected color should be applied consistently to relevant:

* Buttons.
* Links.
* Interactive elements.
* Navigation.
* Focus states.
* Other primary UI elements.

The selected preference should persist.

#### Accessibility

Audit the entire application for color contrast.

Check:

* Text.
* Backgrounds.
* Buttons.
* Links.
* Inputs.
* Cards.
* Navigation.
* Modals.
* Hover states.
* Focus states.
* Disabled states.
* Light mode.
* Dark mode.
* User-selected primary colors.

Fix insufficient contrast where necessary.

Do not use color as the only way to communicate important information.

Ensure keyboard focus remains clearly visible.

#### Acceptance criteria

* Users can switch between supported themes.
* Theme preference persists.
* Users can change the primary color.
* Primary color preference persists.
* UI remains readable after changing the primary color.
* Light and dark themes meet reasonable accessibility contrast requirements.
* Interactive elements have visible focus states.

---

### 5. Free Access

The core application must remain free for everyone.

There must be no mandatory:

* Subscription.
* Payment.
* Credit card.
* Premium account.
* Paid tier

required to use the core website.

Authentication may be required for account-specific functionality such as:

* Creating adventures.
* Editing adventures.
* Editing a profile.

Review the existing project for unnecessary payment or subscription barriers.

Do not remove existing payment functionality blindly. Determine whether it is actually required by the application before changing it.

Prefer existing infrastructure and reasonable free/open-source solutions where possible.

---

## Technical constraints

### Existing project first

Before implementation:

* Inspect the repository.
* Identify the framework and build system.
* Identify authentication.
* Identify database/schema.
* Identify API/backend architecture.
* Identify existing adventure functionality.
* Identify the design system.
* Identify theme handling.
* Identify reusable UI components.

Do not assume any technology.

### Reuse existing architecture

Prefer:

* Existing components.
* Existing hooks/utilities.
* Existing API patterns.
* Existing database models.
* Existing authentication provider.
* Existing design tokens.
* Existing form validation.
* Existing upload infrastructure.

Avoid unnecessary dependencies.

Avoid rewriting unrelated parts of the application.

### Security

* Validate user input.
* Enforce authorization on the server/backend.
* Protect user-owned resources.
* Never expose secrets.
* Use environment variables for credentials.
* Do not trust client-side ownership checks.

### UX

All new functionality should include appropriate:

* Loading states.
* Empty states.
* Error states.
* Success feedback.
* Form validation.
* Mobile layouts.
* Desktop layouts.

The new UI should look native to the existing application.

---

## Implementation strategy

Work incrementally:

1. Inspect the existing project.
2. Identify relevant existing functionality and gaps.
3. Define the required data/model changes.
4. Implement authentication changes.
5. Implement profile functionality.
6. Implement adventure creation and ownership.
7. Implement appearance settings.
8. Perform the accessibility/contrast audit.
9. Verify the free-access requirements.
10. Run tests, linting, type checking, and production build.
11. Fix regressions.

Do not implement a feature twice if a partial implementation already exists.

---

## Definition of Done

The change is complete when:

* Google login works.
* Existing authentication still works.
* Users have profiles.
* Users can create, edit, view, and delete their own adventures.
* Ownership and authorization are enforced.
* Appearance settings work and persist.
* Light/dark themes work consistently.
* Primary color customization works.
* Contrast issues have been audited and fixed.
* The core website remains free to use.
* The application works responsively.
* No secrets are committed.
* Tests/type checks/lint/build pass, or any existing unrelated failures are clearly documented.
* Required environment variables and migrations are documented.

## Final implementation report

After implementation, report:

* Files/components changed.
* Database/schema changes.
* Migrations required.
* Environment variables required.
* OAuth configuration required.
* Tests/checks executed.
* Any existing issues discovered.
* Any remaining manual setup required.

Do not rewrite unrelated parts of the project.
Do not introduce unnecessary dependencies.
Prioritize compatibility with the existing architecture.
