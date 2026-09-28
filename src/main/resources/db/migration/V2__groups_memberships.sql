create table groups (
    id         bigint generated always as identity primary key,
    name       varchar(80) not null,
    created_at timestamptz not null default now()
);

-- A member who leaves keeps the row (left_at set), so their content still resolves to a name.
-- Deleting a group removes its memberships through the cascade.
create table memberships (
    id        bigint generated always as identity primary key,
    group_id  bigint      not null references groups (id) on delete cascade,
    user_id   bigint      not null references users (id),
    role      varchar(10) not null,
    joined_at timestamptz not null default now(),
    left_at   timestamptz,
    constraint uq_memberships_group_user unique (group_id, user_id),
    constraint ck_memberships_role check (role in ('OWNER', 'MEMBER'))
);

-- Exactly one owner per group among the active members.
create unique index uq_memberships_active_owner on memberships (group_id)
    where role = 'OWNER' and left_at is null;

-- "My groups" and the header switcher look memberships up by user.
create index ix_memberships_user_active on memberships (user_id) where left_at is null;
