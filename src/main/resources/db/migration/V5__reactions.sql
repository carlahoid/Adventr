-- At most one reaction per user and adventure; the primary key enforces it, even for a
-- concurrent double click. Reactions of former members stay (labeled in the UI).
create table reactions (
    adventure_id bigint      not null references adventures (id) on delete cascade,
    user_id      bigint      not null references users (id),
    type         varchar(4)  not null,
    created_at   timestamptz not null default now(),
    constraint pk_reactions primary key (adventure_id, user_id),
    constraint ck_reactions_type check (type in ('UP', 'DOWN'))
);
