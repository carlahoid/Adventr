create table users (
    id           bigint generated always as identity primary key,
    keycloak_sub varchar(255) not null,
    display_name varchar(255) not null,
    email        varchar(320),
    created_at   timestamptz  not null default now(),
    deleted_at   timestamptz,
    constraint uq_users_keycloak_sub unique (keycloak_sub)
);
