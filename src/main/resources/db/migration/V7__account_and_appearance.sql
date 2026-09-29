-- Profile and appearance settings on the user.
--   display_name_custom: the user set display_name themselves; login no longer overwrites it.
--   accent_color:        null means the default color; the stored value is always the user's pick.
alter table users
    add column display_name_custom boolean      not null default false,
    add column bio                 varchar(160),
    add column avatar_path         varchar(255),
    add column theme               varchar(6)   not null default 'SYSTEM',
    add column accent_color        char(7),
    add constraint ck_users_theme check (theme in ('LIGHT', 'DARK', 'SYSTEM')),
    add constraint ck_users_accent_color check (accent_color ~ '^#[0-9a-f]{6}$');
