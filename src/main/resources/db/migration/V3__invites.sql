-- One invite link per group; regenerating replaces token and expiry in place.
create table invites (
    id         bigint generated always as identity primary key,
    group_id   bigint       not null references groups (id) on delete cascade,
    token      varchar(64)  not null,
    expires_at timestamptz  not null,
    created_by bigint       not null references users (id),
    created_at timestamptz  not null default now(),
    constraint uq_invites_group unique (group_id),
    constraint uq_invites_token unique (token)
);
