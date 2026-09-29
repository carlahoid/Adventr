# groups Specification

## Purpose
TBD - created by archiving change add-friend-adventures-mvp. Update Purpose after archive.
## Requirements
### Requirement: Central membership authorization
Every request that reads or modifies group-scoped data (the group, its members, invites, adventures, reactions, comments, and images) SHALL be authorized by a single service-layer check that the current user is an active member of that group. Child resources SHALL be looked up scoped to the group ID from the URL. Non-members SHALL receive HTTP 404.

#### Scenario: Non-member accesses group
- **WHEN** a user who is not an active member requests `/groups/7`
- **THEN** the system responds with HTTP 404 and reveals no group data

#### Scenario: Cross-group ID tampering
- **WHEN** a member of group 7 requests `/groups/7/adventures/99`, and adventure 99 belongs to group 8
- **THEN** the system responds with HTTP 404

#### Scenario: Former member loses access
- **WHEN** a user who left group 7 requests any page or action under `/groups/7`
- **THEN** the system responds with HTTP 404

### Requirement: Create group
Any authenticated user SHALL be able to create a group by entering a name (1–80 characters). The creator SHALL become the group's Owner.

#### Scenario: User creates a group
- **WHEN** a user submits "Create group" with the name "Mountain Crew"
- **THEN** the group is created, the user is its Owner, and they are redirected to the group's adventure list

#### Scenario: Empty name rejected
- **WHEN** a user submits "Create group" with a blank name
- **THEN** no group is created and a validation message is shown

### Requirement: My groups landing page
After login, the system SHALL show the user a "My groups" page listing every group they are an active member of, each with its name and member count, and a "Create group" button.

#### Scenario: User with groups
- **WHEN** a user who belongs to two groups opens the app
- **THEN** they see both groups and can open either one

#### Scenario: User without groups
- **WHEN** a user with no memberships opens the app
- **THEN** they see an empty state explaining that they can create a group or ask a friend for an invite link

### Requirement: Group switcher
Every page inside a group SHALL show a header control listing the user's groups, which navigates to the selected group's adventure list.

#### Scenario: Switch group
- **WHEN** a user viewing group A selects group B in the header switcher
- **THEN** they are navigated to `/groups/{B}`

### Requirement: Roles
Each active membership SHALL have exactly one role: `OWNER` or `MEMBER`. Each group SHALL have exactly one Owner. Members SHALL be able to add adventures, react, comment, change any adventure's status, and edit or delete their own content. The Owner SHALL additionally be able to rename the group, manage invites, remove members, transfer ownership, delete any adventure or comment, and delete the group.

#### Scenario: Member attempts owner action
- **WHEN** a Member submits a request to remove another member
- **THEN** the system rejects it with HTTP 403 and the membership is unchanged

### Requirement: Group settings page
The group settings page SHALL show the member list with roles and join dates to all members. For the Owner, it SHALL also show controls to rename the group, generate or regenerate the invite link, remove members, transfer ownership, and delete the group. Every member SHALL see a "Leave group" action.

#### Scenario: Owner renames group
- **WHEN** the Owner changes the group name to "Mountain Crew 2026"
- **THEN** the new name appears in the settings, the list header, and the group switcher

### Requirement: Leave group
An active Member SHALL be able to leave a group. The Owner SHALL NOT be able to leave while other active members exist and SHALL be prompted to transfer ownership first. If the Owner is the only active member, leaving SHALL delete the group.

#### Scenario: Member leaves
- **WHEN** a Member confirms "Leave group"
- **THEN** their membership becomes inactive, they are redirected to "My groups", and their adventures, reactions, and comments remain

#### Scenario: Owner tries to leave with members present
- **WHEN** the Owner of a group with other active members clicks "Leave group"
- **THEN** the system refuses and asks them to transfer ownership first

#### Scenario: Sole owner leaves
- **WHEN** the Owner is the only active member and confirms "Leave group"
- **THEN** the group and all of its content are deleted

### Requirement: Remove member
The Owner SHALL be able to remove any other member. Removed members' content SHALL remain.

#### Scenario: Owner removes a member
- **WHEN** the Owner removes member Alex
- **THEN** Alex loses access to the group, and Alex's adventures and comments remain, attributed to Alex with a "former member" label

### Requirement: Former member attribution
Content created by a user without an active membership in the group SHALL display the author's last known display name with a "former member" label.

#### Scenario: Viewing a former member's comment
- **WHEN** a member views an adventure that has a comment by a user who left the group
- **THEN** the comment shows the author's name followed by "(former member)"

### Requirement: Transfer ownership
The Owner SHALL be able to transfer ownership to another active member. After the transfer, the previous Owner SHALL become a Member.

#### Scenario: Owner transfers ownership
- **WHEN** the Owner transfers ownership to member Sam
- **THEN** Sam becomes the Owner and the previous Owner becomes a Member

### Requirement: Automatic owner promotion on account deletion
If the Owner's user account is deleted, the system SHALL promote the longest-standing active member (earliest join date) to Owner. If no other active members exist, the group SHALL be deleted.

#### Scenario: Owner account deleted
- **WHEN** the Owner's account is deleted and members Kim (joined first) and Lee remain
- **THEN** Kim becomes the Owner

### Requirement: Delete group
The Owner SHALL be able to delete the group after an explicit confirmation (typing the group name). Deletion SHALL remove all of the group's memberships, invites, adventures, reactions, comments, and image files.

#### Scenario: Owner deletes group
- **WHEN** the Owner confirms deletion by typing the group name
- **THEN** the group and all associated data and image files are removed, and former members no longer see it in "My groups"

