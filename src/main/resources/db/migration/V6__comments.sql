-- A flat, chronological thread per adventure.
create table comments (
    id           bigint generated always as identity primary key,
    adventure_id bigint        not null references adventures (id) on delete cascade,
    author_id    bigint        not null references users (id),
    text         varchar(2000) not null,
    created_at   timestamptz   not null default now(),
    edited_at    timestamptz
);

-- The thread is loaded oldest first; the list counts comments per adventure.
create index ix_comments_adventure on comments (adventure_id, created_at, id);
