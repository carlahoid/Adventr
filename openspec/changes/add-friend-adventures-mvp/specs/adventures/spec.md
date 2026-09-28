## ADDED Requirements

### Requirement: Adventure fields
An adventure SHALL belong to exactly one group and have a creator. `title` (1–120 characters) SHALL be the only required field. The optional fields SHALL be:
- `description` (up to 5000 characters)
- `location` (free text, up to 200 characters)
- `date_from` and `date_to`, where `date_to` SHALL NOT be before `date_from`
- `time_hint` (free text, up to 100 characters)
- `cost_amount` (non-negative decimal) and `cost_note` (up to 100 characters)
- `link` (an http or https URL)
- one image

Every adventure SHALL have a `status` of `IDEA`, `PLANNED`, or `DONE`, defaulting to `IDEA`, and SHALL record its creation and last update times.

#### Scenario: Invalid date range
- **WHEN** a member saves an adventure with `date_to` before `date_from`
- **THEN** the system rejects the save with a validation message

#### Scenario: Non-http link rejected
- **WHEN** a member enters `javascript:alert(1)` as the link
- **THEN** the system rejects the save with a validation message

### Requirement: Quick add
The adventure list SHALL offer an inline quick-add form that needs only a title, so that a member can add an idea in about five seconds without leaving the list.

#### Scenario: Quick add an idea
- **WHEN** a member types "Canoe trip" into the quick-add field and presses Enter
- **THEN** a new adventure with status `IDEA` appears in the Ideas section without a full page reload

### Requirement: Edit and delete adventures
The creator SHALL be able to edit all fields of their adventure and delete it. The Owner SHALL be able to delete any adventure. Deleting an adventure SHALL remove its reactions, comments, and image file.

#### Scenario: Creator edits adventure
- **WHEN** the creator changes the location and saves
- **THEN** the detail page shows the new location and an updated "last updated" time

#### Scenario: Other member cannot edit
- **WHEN** a Member who is not the creator submits an edit to the adventure's fields
- **THEN** the system rejects it with HTTP 403

#### Scenario: Owner deletes another member's adventure
- **WHEN** the Owner deletes an adventure created by someone else
- **THEN** the adventure, its reactions, comments, and image are removed

### Requirement: Status changes by any member
Any active member SHALL be able to change any adventure's status between `IDEA`, `PLANNED`, and `DONE`. The system SHALL record when the status last changed.

#### Scenario: Member marks adventure done
- **WHEN** a member who is not the creator sets an adventure's status to `DONE`
- **THEN** the status changes and the adventure moves to the Memories section

### Requirement: Adventure list page
`/groups/{id}` SHALL list the group's adventures in three sections, in this order:
- **Planned**: `PLANNED` adventures, ordered by `date_from` ascending (no date last), then net score descending, then newest first.
- **Ideas**: `IDEA` adventures, ordered by net score descending, then newest first.
- **Memories**: `DONE` adventures, ordered by most recent status change first.

Each item SHALL show the title, 👍 and 👎 counts, the comment count, the current user's own reaction state, and a thumbnail if an image exists.

#### Scenario: Sections and ordering
- **WHEN** a group has one PLANNED, two IDEA (net scores +3 and +1), and one DONE adventure
- **THEN** the list shows the PLANNED one first, then the IDEA with +3, then the IDEA with +1, and then the DONE one under "Memories"

#### Scenario: Empty list
- **WHEN** a group has no adventures
- **THEN** the page shows an empty state inviting members to add the first idea

### Requirement: Adventure detail page
`/groups/{gid}/adventures/{aid}` SHALL show all set fields of the adventure, its creator, the status control, the image, the reaction buttons with the names of who reacted, and the comment thread with an input box. Unset optional fields SHALL be omitted, not shown as empty.

#### Scenario: Open detail page
- **WHEN** a member clicks an adventure in the list
- **THEN** the detail page shows its fields, reactions with names, and its comments

### Requirement: Single image upload
The creator SHALL be able to attach, replace, or remove one image per adventure. Accepted formats SHALL be JPEG, PNG, and WebP, verified by file content, up to 10 MB. The system SHALL apply EXIF orientation, strip metadata, resize the image to at most 1600 px on its longer side, and store it compressed on disk. Replacing or removing an image SHALL delete the previous file.

#### Scenario: Upload a photo
- **WHEN** the creator uploads a 6 MB, 4000×3000 JPEG
- **THEN** the system stores a re-encoded image no larger than 1600×1200 and shows it on the detail page

#### Scenario: Reject non-image
- **WHEN** a user uploads a PDF renamed to `photo.jpg`
- **THEN** the system rejects it with a validation message and stores nothing

#### Scenario: Reject oversized file
- **WHEN** a user uploads a 15 MB image
- **THEN** the system rejects it with a message stating the 10 MB limit

### Requirement: Protected image delivery
Images SHALL be served only through an application endpoint scoped to the group and adventure, and that endpoint SHALL enforce the central membership check. Image files SHALL NOT be reachable through any static or public path.

#### Scenario: Non-member requests image URL
- **WHEN** a non-member requests `/groups/7/adventures/12/image`
- **THEN** the system responds with HTTP 404

#### Scenario: Member views image
- **WHEN** a member requests the image of an adventure in their group
- **THEN** the system returns the image with `Cache-Control: private`

### Requirement: Safe rendering of user content
All user-provided text (titles, descriptions, comments, locations, and names) SHALL be HTML-escaped when rendered. Links SHALL open with `rel="noopener noreferrer"`.

#### Scenario: Script in title
- **WHEN** a member creates an adventure titled `<script>alert(1)</script>`
- **THEN** the title is displayed as literal text and no script runs
