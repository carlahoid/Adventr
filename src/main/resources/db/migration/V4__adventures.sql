-- An adventure belongs to one group; deleting the group deletes its adventures (and, through
-- their own cascades, reactions and comments). Image files are removed by the app after commit.
create table adventures (
    id                bigint generated always as identity primary key,
    group_id          bigint        not null references groups (id) on delete cascade,
    created_by        bigint        not null references users (id),
    title             varchar(120)  not null,
    description       varchar(5000),
    location          varchar(200),
    date_from         date,
    date_to           date,
    time_hint         varchar(100),
    cost_amount       numeric(10, 2),
    cost_note         varchar(100),
    link              varchar(2000),
    image_path        varchar(255),
    status            varchar(10)   not null default 'IDEA',
    status_changed_at timestamptz   not null default now(),
    created_at        timestamptz   not null default now(),
    updated_at        timestamptz   not null default now(),
    constraint ck_adventures_status check (status in ('IDEA', 'PLANNED', 'DONE')),
    constraint ck_adventures_dates check (date_to is null or date_from is null or date_to >= date_from),
    constraint ck_adventures_cost check (cost_amount is null or cost_amount >= 0)
);

-- The list page loads one group's adventures and splits them by status.
create index ix_adventures_group_status on adventures (group_id, status);
