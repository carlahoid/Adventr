## ADDED Requirements

### Requirement: One reaction per user per adventure
Each user SHALL have at most one reaction per adventure, of type `UP` (👍) or `DOWN` (👎). This SHALL be enforced by a database uniqueness constraint on (adventure, user).

#### Scenario: Concurrent double click
- **WHEN** the same user's two 👍 requests for one adventure arrive simultaneously
- **THEN** at most one reaction row exists for that user and adventure afterwards

### Requirement: Toggle semantics
Clicking a reaction button SHALL change the user's reaction as follows:

| Current state | Clicked 👍 | Clicked 👎 |
|---|---|---|
| none | 👍 | 👎 |
| 👍 | none | 👎 |
| 👎 | 👍 | none |

#### Scenario: Add thumbs up
- **WHEN** a user with no reaction clicks 👍
- **THEN** their reaction becomes 👍 and the 👍 count increases by one

#### Scenario: Remove thumbs up
- **WHEN** a user whose reaction is 👍 clicks 👍
- **THEN** their reaction is removed and the 👍 count decreases by one

#### Scenario: Switch from up to down
- **WHEN** a user whose reaction is 👍 clicks 👎
- **THEN** their reaction becomes 👎, 👍 decreases by one, and 👎 increases by one

#### Scenario: Switch from down to up
- **WHEN** a user whose reaction is 👎 clicks 👍
- **THEN** their reaction becomes 👍

#### Scenario: Remove thumbs down
- **WHEN** a user whose reaction is 👎 clicks 👎
- **THEN** their reaction is removed

### Requirement: In-place update
Reacting SHALL update the reaction bar (counts and own state) in place via an htmx fragment, without a full page reload, on both the list and the detail page. Without JavaScript, it SHALL fall back to a normal form post and redirect.

#### Scenario: React from the list
- **WHEN** a user clicks 👍 on an item in the adventure list
- **THEN** only that item's reaction bar is re-rendered, showing the new counts and the highlighted 👍

### Requirement: Separate counts and net-score sorting
The system SHALL display 👍 and 👎 counts separately (e.g. "👍 5 · 👎 1"). The net score (👍 minus 👎) SHALL be used for ordering the adventure list as defined in the adventures capability.

#### Scenario: Counts display
- **WHEN** an adventure has 5 up and 1 down reactions
- **THEN** it shows "👍 5 · 👎 1"

### Requirement: Reactor names visible
The system SHALL show who reacted: as a tooltip on the counts in the list, and as name lists for 👍 and 👎 on the detail page. Former members' reactions SHALL remain, labeled "former member".

#### Scenario: Names on detail page
- **WHEN** Anna and Ben reacted 👍 and Chris reacted 👎
- **THEN** the detail page lists "👍 Anna, Ben" and "👎 Chris"

### Requirement: Reactions independent of comments
Reacting SHALL NOT require commenting, and commenting SHALL NOT require reacting.

#### Scenario: React without comment
- **WHEN** a user reacts 👎 and writes no comment
- **THEN** the reaction is saved and no comment is created
