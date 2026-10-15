-- users of the bot: the owners of the data in all skills, which refer to them by id without foreign keys
create table users
(
    id            bigint generated always as identity primary key,
    admin         boolean     not null default false,
    -- Web UI login, chosen on the first login with a link from the bot
    login         text unique,
    password_hash text,
    created_at    timestamptz not null default now(),
    check ((login is null) = (password_hash is null))
);

create unique index users_single_admin on users (admin) where admin;

-- Telegram accounts of the users; tg_user_id is also the id of the private chat with the bot
create table telegram_user
(
    tg_user_id bigint primary key,
    user_id    bigint      not null references users (id) on delete cascade,
    created_at timestamptz not null default now()
);

create index telegram_user_user_id on telegram_user (user_id);
