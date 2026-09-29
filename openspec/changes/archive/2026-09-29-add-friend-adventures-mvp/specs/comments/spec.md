## ADDED Requirements

### Requirement: Post comment
Any active member SHALL be able to post a comment (1–2000 characters of plain text) on an adventure in their group. Line breaks SHALL be preserved on display.

#### Scenario: Member posts comment
- **WHEN** a member submits "I'm in, but only in August!" on the detail page
- **THEN** the comment appears at the bottom of the thread with their name and timestamp, without a full page reload

#### Scenario: Empty comment rejected
- **WHEN** a member submits a blank comment
- **THEN** no comment is created and a validation message is shown

### Requirement: Flat, chronological thread
Comments SHALL be displayed as a single flat list, oldest first, with no replies or nesting. The adventure list SHALL show each adventure's comment count.

#### Scenario: Thread ordering
- **WHEN** comments were posted at 10:00, 10:05, and 10:10
- **THEN** they are displayed in that order

### Requirement: Edit own comment
A comment's author SHALL be able to edit its text. Edited comments SHALL record the edit time and display "(edited)".

#### Scenario: Author edits comment
- **WHEN** the author changes their comment text and saves
- **THEN** the new text is shown with an "(edited)" marker

#### Scenario: Non-author cannot edit
- **WHEN** a user who is not the author, including the Owner, submits an edit to a comment
- **THEN** the system rejects it with HTTP 403

### Requirement: Delete comment
A comment's author SHALL be able to delete their own comment, and the group Owner SHALL be able to delete any comment in the group. Deletion SHALL remove the comment from the thread.

#### Scenario: Author deletes comment
- **WHEN** the author deletes their comment
- **THEN** it disappears from the thread and the comment count decreases by one

#### Scenario: Owner deletes someone else's comment
- **WHEN** the Owner deletes another member's comment
- **THEN** the comment is removed

#### Scenario: Member cannot delete others' comments
- **WHEN** a Member tries to delete another member's comment
- **THEN** the system rejects it with HTTP 403
